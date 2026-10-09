import Foundation
#if canImport(Darwin)
import Darwin
#else
import Glibc
#endif

public enum InspectionError: Error {
    case invalidInput, revisionConflict, alreadyExists, storageBusy, invalidReceipt, flushInProgress
}

public struct Inspection: Codable, Sendable {
    public let id: UUID
    public let revision: Int
    public let asset: String
    public let operatorName: String
    public let checks: [String: Bool]
    public let notes: String
    public let recordedAt: Date
}

public struct DeliverySummary: Sendable {
    public let sent: Int
    public let failed: Int
    public let pending: Int
}

public protocol ArchiveTransport: Sendable {
    func send(_ body: Data) async throws -> Data
}

private struct Envelope: Encodable {
    let project = "android-offshore-field-console"
    let kind = "inspection"
    let clientKey: String
    let result: Inspection
    let events: [String] = []
}

private struct Pending: Codable {
    let payload: Data
    var attempts: Int
    var nextAttempt: Date
}

private struct StoreState: Codable {
    let version: Int
    var inspections: [String: Inspection]
    var outbox: [String: Pending]
}

public actor InspectionStore {
    private let file: URL
    private let lockDescriptor: Int32
    private var state: StoreState
    private var flushing = false

    public init(file: URL) throws {
        guard file.isFileURL else { throw InspectionError.invalidInput }
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        let descriptor = open(file.path + ".lock", O_CREAT | O_RDWR | O_NOFOLLOW, 0o600)
        guard descriptor >= 0 else { throw InspectionError.storageBusy }
        guard flock(descriptor, LOCK_EX | LOCK_NB) == 0 else {
            close(descriptor)
            throw InspectionError.storageBusy
        }
        do {
            let loaded: StoreState
            if FileManager.default.fileExists(atPath: file.path) {
                let attributes = try FileManager.default.attributesOfItem(atPath: file.path)
                guard attributes[.type] as? FileAttributeType == .typeRegular,
                      ((attributes[.size] as? NSNumber)?.intValue ?? Int.max) <= 32 * 1024 * 1024 else {
                    throw InspectionError.invalidInput
                }
                loaded = try JSONDecoder().decode(StoreState.self, from: Data(contentsOf: file))
                guard loaded.version == 1, loaded.outbox.count <= 10_000 else { throw InspectionError.invalidInput }
                for (key, pending) in loaded.outbox {
                    guard key.utf8.count <= 128, !key.isEmpty, pending.payload.count <= 262144,
                          (0...31).contains(pending.attempts), pending.nextAttempt.timeIntervalSince1970.isFinite else {
                        throw InspectionError.invalidInput
                    }
                }
                for (key, record) in loaded.inspections {
                    guard key == record.id.uuidString, record.revision > 0 else { throw InspectionError.invalidInput }
                    try Self.validate(asset: record.asset, operatorName: record.operatorName, checks: record.checks, notes: record.notes)
                }
            } else {
                loaded = StoreState(version: 1, inspections: [:], outbox: [:])
            }
            self.file = file
            self.lockDescriptor = descriptor
            self.state = loaded
        } catch {
            flock(descriptor, LOCK_UN)
            close(descriptor)
            throw error
        }
    }

    deinit {
        flock(lockDescriptor, LOCK_UN)
        close(lockDescriptor)
    }

    private static func validate(asset: String, operatorName: String, checks: [String: Bool], notes: String) throws {
        guard !asset.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, asset.utf8.count <= 120,
              !operatorName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, operatorName.utf8.count <= 120,
              (1...50).contains(checks.count), notes.utf8.count <= 4000,
              checks.keys.allSatisfy({ $0.range(of: "^[A-Za-z][A-Za-z0-9_-]{0,63}$", options: .regularExpression) != nil }) else {
            throw InspectionError.invalidInput
        }
    }

    private func commit(_ next: StoreState) throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(next)
        guard data.count <= 32 * 1024 * 1024 else { throw InspectionError.invalidInput }
        try data.write(to: file, options: [.atomic])
        state = next
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: file.path)
        let handle = try FileHandle(forWritingTo: file)
        defer { try? handle.close() }
        try handle.synchronize()
    }

    @discardableResult
    public func save(id: UUID = UUID(), expectedRevision: Int = 0, asset: String, operatorName: String,
                     checks: [String: Bool], notes: String, now: Date = Date()) throws -> Inspection {
        try Self.validate(asset: asset, operatorName: operatorName, checks: checks, notes: notes)
        guard now.timeIntervalSince1970.isFinite, expectedRevision >= 0, expectedRevision < Int.max,
              state.outbox.count < 10_000 else { throw InspectionError.invalidInput }
        let previous = state.inspections[id.uuidString]
        guard (previous?.revision ?? 0) == expectedRevision else { throw InspectionError.revisionConflict }
        let record = Inspection(id: id, revision: expectedRevision + 1, asset: asset, operatorName: operatorName,
                                checks: checks, notes: notes, recordedAt: now)
        let key = id.uuidString.lowercased() + "-" + String(record.revision)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        let payload = try encoder.encode(Envelope(clientKey: key, result: record))
        var next = state
        next.inspections[id.uuidString] = record
        next.outbox[key] = Pending(payload: payload, attempts: 0, nextAttempt: .distantPast)
        try commit(next)
        return record
    }

    public func records() -> [Inspection] {
        state.inspections.values.sorted {
            $0.recordedAt == $1.recordedAt ? $0.id.uuidString < $1.id.uuidString : $0.recordedAt < $1.recordedAt
        }
    }

    public func pendingCount() -> Int { state.outbox.count }

    public func flush(using transport: any ArchiveTransport, limit: Int = 100, now: Date = Date()) async throws -> DeliverySummary {
        guard (1...100).contains(limit), now.timeIntervalSince1970.isFinite else { throw InspectionError.invalidInput }
        guard !flushing else { throw InspectionError.flushInProgress }
        flushing = true
        defer { flushing = false }
        var sent = 0
        var failed = 0
        let keys = state.outbox.filter { $0.value.nextAttempt <= now }.keys.sorted().prefix(limit)
        for key in keys {
            guard var item = state.outbox[key] else { continue }
            var accepted = false
            do {
                let data = try await transport.send(item.payload)
                guard data.count <= 4096,
                      let receipt = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      receipt["persisted"] as? Bool == true, receipt["clientKey"] as? String == key else {
                    throw InspectionError.invalidReceipt
                }
                accepted = true
            } catch {
                accepted = false
            }
            var next = state
            if accepted {
                next.outbox.removeValue(forKey: key)
                sent += 1
            } else {
                item.attempts = min(item.attempts, 30) + 1
                item.nextAttempt = now.addingTimeInterval(min(3600, pow(2, Double(min(item.attempts, 12)))))
                next.outbox[key] = item
                failed += 1
            }
            try commit(next)
        }
        return DeliverySummary(sent: sent, failed: failed, pending: state.outbox.count)
    }
}

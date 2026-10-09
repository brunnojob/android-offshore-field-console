import Foundation

struct ReceiptTransport: ArchiveTransport {
    let wrongKey: Bool
    func send(_ body: Data) async throws -> Data {
        let value = try JSONSerialization.jsonObject(with: body) as! [String: Any]
        return try JSONSerialization.data(withJSONObject: ["persisted": true,
          "clientKey": wrongKey ? "wrong" : value["clientKey"] as! String])
    }
}

@main struct StoreChecks {
    static func main() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("inspections.json")
        let store = try InspectionStore(file: file)
        let now = Date(timeIntervalSince1970: 1000)
        let first = try await store.save(asset: "pump-01", operatorName: "operator", checks: ["guard": true], notes: "checked", now: now)
        do {
            _ = try await store.save(id: first.id, expectedRevision: 0, asset: "pump-01", operatorName: "operator",
                                     checks: ["guard": true], notes: "stale", now: now)
            fatalError("stale revision accepted")
        } catch InspectionError.revisionConflict {}
        let failed = try await store.flush(using: ReceiptTransport(wrongKey: true), now: now)
        precondition(failed.failed == 1 && failed.pending == 1)
        let deferred = try await store.flush(using: ReceiptTransport(wrongKey: false), now: now)
        precondition(deferred.sent == 0 && deferred.pending == 1)
        let success = try await store.flush(using: ReceiptTransport(wrongKey: false), now: now.addingTimeInterval(3))
        precondition(success.sent == 1 && success.pending == 0)
        do {
            _ = try InspectionStore(file: file)
            fatalError("concurrent store accepted")
        } catch InspectionError.storageBusy {}
        let second = try await store.save(id: first.id, expectedRevision: 1, asset: "pump-01", operatorName: "operator",
                                         checks: ["guard": false], notes: "requires repair", now: now.addingTimeInterval(4))
        precondition(second.revision == 2)
        let snapshot = await store.records()
        precondition(snapshot.count == 1 && snapshot[0].revision == 2)
        print("Swift inspection checks passed")
    }
}

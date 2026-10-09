import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

private final class RejectRedirects: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

public struct HTTPArchiveTransport: ArchiveTransport {
    private let endpoint: URL
    private let token: String

    public init(endpoint: URL, token: String) throws {
        guard endpoint.scheme == "https", endpoint.host != nil, endpoint.user == nil, endpoint.password == nil,
              endpoint.fragment == nil, token.utf8.count <= 8192,
              token.range(of: "^[A-Za-z0-9_.-]+$", options: .regularExpression) != nil else {
            throw InspectionError.invalidInput
        }
        self.endpoint = endpoint
        self.token = token
    }

    public func send(_ body: Data) async throws -> Data {
        guard body.count <= 262144 else { throw InspectionError.invalidInput }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 15
        let session = URLSession(configuration: configuration, delegate: RejectRedirects(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode), data.count <= 4096 else {
            throw InspectionError.invalidReceipt
        }
        return data
    }
}

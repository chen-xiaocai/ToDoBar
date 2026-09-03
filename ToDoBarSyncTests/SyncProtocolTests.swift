import XCTest
@testable import ToDoBarSync

final class SyncProtocolTests: XCTestCase {
    func testEncryptedRoundTripAndAuthentication() throws {
        let key = SyncCrypto.randomKey()
        let input = SyncRequest(items: [IncomingTodo(id: "41e4e5e5-7c80-476d-a7e1-aca0a728edb5", text: "sample", createdAt: 1)])
        let envelope = try SyncCrypto.seal(input, kind: "sync", serverID: "server", deviceID: "device", key: key)
        let output = try SyncCrypto.open(SyncRequest.self, envelope: envelope, key: key)
        XCTAssertEqual(output.items.first?.id, input.items.first?.id)
        XCTAssertThrowsError(try SyncCrypto.open(SyncRequest.self, envelope: envelope, key: SyncCrypto.randomKey()))
    }
}

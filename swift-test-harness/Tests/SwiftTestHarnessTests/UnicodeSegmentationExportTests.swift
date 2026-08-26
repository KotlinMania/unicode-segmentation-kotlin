#if canImport(Testing)
import Testing
import UnicodeSegmentation

@Suite("UnicodeSegmentation Swift Export Suite")
struct UnicodeSegmentationExportTests {
    @Test("Swift module loads cleanly")
    func swiftModuleLoads() {
        #expect(Bool(true), "UnicodeSegmentation swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import UnicodeSegmentation

final class UnicodeSegmentationExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "UnicodeSegmentation swift module imported cleanly")
    }
}
#endif


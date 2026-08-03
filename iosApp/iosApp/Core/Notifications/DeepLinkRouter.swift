import Foundation
import Combine

@MainActor
final class DeepLinkRouter: ObservableObject {
    static let shared = DeepLinkRouter()

    enum Destination: Equatable {
        case serviceAlert(id: String)
        case weatherAlert
        case morningDigest
        case nearbyAlert
    }

    @Published var pending: Destination?

    func consume() -> Destination? {
        guard let dest = pending else { return nil }
        pending = nil
        return dest
    }
}

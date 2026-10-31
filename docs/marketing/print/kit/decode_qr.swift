// Prints "<path>\t<payload>" for every QR code CoreImage finds in each PNG.
// Newlines inside a payload (the sticker guides) are printed as a literal \n.
import CoreImage
import Foundation

let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: nil,
                          options: [CIDetectorAccuracy: CIDetectorAccuracyHigh])!
for path in CommandLine.arguments.dropFirst() {
    guard let image = CIImage(contentsOf: URL(fileURLWithPath: path)) else {
        print("\(path)\tERROR unreadable")
        continue
    }
    let found = detector.features(in: image).compactMap { ($0 as? CIQRCodeFeature)?.messageString }
    if found.isEmpty { print("\(path)\tNONE") }
    for message in found { print("\(path)\t\(message.replacingOccurrences(of: "\n", with: "\\n"))") }
}

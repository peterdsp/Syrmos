import SwiftUI

struct TransitMapView: View {
    var body: some View {
        NavigationStack {
            Canvas { context, size in
                let w = size.width
                let h = size.height

                // Line 1 (Green): vertical
                var line1 = Path()
                line1.move(to: CGPoint(x: w * 0.3, y: h * 0.08))
                line1.addLine(to: CGPoint(x: w * 0.3, y: h * 0.92))
                context.stroke(line1, with: .color(.metroGreen), lineWidth: 4)

                // Line 2 (Red): horizontal
                var line2 = Path()
                line2.move(to: CGPoint(x: w * 0.08, y: h * 0.45))
                line2.addLine(to: CGPoint(x: w * 0.92, y: h * 0.45))
                context.stroke(line2, with: .color(.metroRed), lineWidth: 4)

                // Line 3 (Blue): diagonal
                var line3 = Path()
                line3.move(to: CGPoint(x: w * 0.08, y: h * 0.78))
                line3.addLine(to: CGPoint(x: w * 0.92, y: h * 0.15))
                context.stroke(line3, with: .color(.metroBlue), lineWidth: 4)

                // Interchange dots
                let interchanges: [(CGFloat, CGFloat, String)] = [
                    (0.30, 0.45, "Omonia"),
                    (0.38, 0.45, "Syntagma"),
                    (0.30, 0.52, "Monastiraki"),
                ]

                for (x, y, _) in interchanges {
                    let center = CGPoint(x: w * x, y: h * y)
                    context.fill(Circle().path(in: CGRect(x: center.x - 6, y: center.y - 6, width: 12, height: 12)), with: .color(.white))
                    context.stroke(Circle().path(in: CGRect(x: center.x - 6, y: center.y - 6, width: 12, height: 12)), with: .color(.primary), lineWidth: 1.5)
                }
            }
            .background(Color.syrmosBackground)
            .navigationTitle("Transit Map")
        }
    }
}

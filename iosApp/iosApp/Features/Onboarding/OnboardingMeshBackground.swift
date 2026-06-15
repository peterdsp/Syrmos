import SwiftUI

struct OnboardingMeshBackground: View {
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
            MeshCanvas(time: timeline.date.timeIntervalSinceReferenceDate)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

private struct MeshCanvas: View {
    let time: Double

    var body: some View {
        Canvas { context, size in
            drawBlob(context, size: size,
                     color: Color(.sRGB, red: 0/255, green: 131/255, blue: 201/255, opacity: 0.18),
                     xFactor: 0.3, yFactor: 0.2, xFreq: 0.3, yFreq: 0.4, xAmp: 0.15, yAmp: 0.1, radius: 0.45)
            drawBlob(context, size: size,
                     color: Color(.sRGB, red: 226/255, green: 35/255, blue: 26/255, opacity: 0.10),
                     xFactor: 0.7, yFactor: 0.5, xFreq: 0.25, yFreq: 0.35, xAmp: 0.1, yAmp: 0.15, radius: 0.5)
            drawBlob(context, size: size,
                     color: Color(.sRGB, red: 248/255, green: 195/255, blue: 30/255, opacity: 0.08),
                     xFactor: 0.5, yFactor: 0.85, xFreq: 0.2, yFreq: 0.3, xAmp: 0.2, yAmp: 0.1, radius: 0.42)
        }
    }

    private func drawBlob(
        _ context: GraphicsContext, size: CGSize,
        color: Color,
        xFactor: Double, yFactor: Double,
        xFreq: Double, yFreq: Double,
        xAmp: Double, yAmp: Double,
        radius: Double
    ) {
        let w = size.width
        let h = size.height
        let cx = w * (xFactor + xAmp * sin(time * xFreq))
        let cy = h * (yFactor + yAmp * cos(time * yFreq))
        let r = w * radius
        let gradient = Gradient(colors: [color, color.opacity(0)])
        let shading = GraphicsContext.Shading.radialGradient(
            gradient, center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: r
        )
        context.fill(Path(CGRect(origin: .zero, size: size)), with: shading)
    }
}

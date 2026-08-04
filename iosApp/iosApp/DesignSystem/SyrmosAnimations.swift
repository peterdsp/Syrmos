import SwiftUI

extension Animation {
    static let trainGlide = Animation.timingCurve(0.16, 1.0, 0.30, 1.0, duration: 0.3)

    static func trainGlide(duration: Double) -> Animation {
        .timingCurve(0.16, 1.0, 0.30, 1.0, duration: duration)
    }
}

struct SyrmosEntrance: ViewModifier {
    let index: Int
    @State private var appeared = false

    private var cappedIndex: Int { min(index, 8) }
    private var delay: Double { Double(cappedIndex) * 0.04 }

    func body(content: Content) -> some View {
        content
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 12)
            .animation(
                Animation.trainGlide(duration: 0.45).delay(delay),
                value: appeared
            )
            .onAppear { appeared = true }
    }
}

extension View {
    func syrmosEntrance(index: Int) -> some View {
        modifier(SyrmosEntrance(index: index))
    }
}

struct LivePulse: ViewModifier {
    @State private var pulsing = false

    func body(content: Content) -> some View {
        content
            .scaleEffect(pulsing ? 1.15 : 1.0)
            .opacity(pulsing ? 0.7 : 1.0)
            .animation(
                .easeInOut(duration: 1.0).repeatForever(autoreverses: true),
                value: pulsing
            )
            .onAppear { pulsing = true }
    }
}

extension View {
    func livePulse() -> some View {
        modifier(LivePulse())
    }
}

struct HeroImminentPulse: ViewModifier {
    let active: Bool
    @State private var pulsing = false

    func body(content: Content) -> some View {
        content
            .scaleEffect(active && pulsing ? 1.06 : 1.0)
            .opacity(active && pulsing ? 0.8 : 1.0)
            .animation(
                active ? .easeInOut(duration: 1.0).repeatForever(autoreverses: true) : .default,
                value: pulsing
            )
            .onChange(of: active) { _, isActive in
                pulsing = isActive
            }
            .onAppear { if active { pulsing = true } }
    }
}

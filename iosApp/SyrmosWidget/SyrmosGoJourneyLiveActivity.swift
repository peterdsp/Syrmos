import WidgetKit
import ActivityKit
import SwiftUI

/// Phase N J08: Lock Screen + Dynamic Island UI for an active GO journey. The app
/// (`GoJourneyActivityController`) starts/updates/ends it; this renders it.
/// `GoJourneyActivityAttributes` is shared with the app target. Shows the one
/// instruction that matters, destination, and progress; renders stale content
/// honestly via the system `.stale` treatment (the app sets `staleDate`).
@available(iOS 16.2, *)
struct SyrmosGoJourneyLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: GoJourneyActivityAttributes.self) { context in
            GoLockScreenView(context: context)
                .activityBackgroundTint(tint(context).opacity(0.10))
                .activitySystemActionForegroundColor(tint(context))
        } dynamicIsland: { context in
            let t = tint(context)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: icon(context.state)).foregroundStyle(t)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("\(Int((context.state.progress * 100).rounded()))%")
                        .font(.caption).monospacedDigit().foregroundStyle(t)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.state.stateLabel).font(.caption2).foregroundStyle(.secondary)
                        Text(context.state.instruction).font(.footnote.weight(.semibold)).lineLimit(2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    ProgressView(value: context.state.progress).tint(t)
                }
            } compactLeading: {
                Image(systemName: icon(context.state)).foregroundStyle(t)
            } compactTrailing: {
                Text("\(Int((context.state.progress * 100).rounded()))%")
                    .font(.caption2).monospacedDigit().foregroundStyle(t)
            } minimal: {
                Image(systemName: icon(context.state)).foregroundStyle(t)
            }
        }
    }

    private func tint(_ context: ActivityViewContext<GoJourneyActivityAttributes>) -> Color {
        if let id = context.state.lineId { return SyrmosLineTokens.color(for: id) }
        return .blue
    }

    private func icon(_ state: GoJourneyActivityAttributes.ContentState) -> String {
        if state.arrived { return "checkmark.circle.fill" }
        return "figure.walk"
    }
}

@available(iOS 16.2, *)
private struct GoLockScreenView: View {
    let context: ActivityViewContext<GoJourneyActivityAttributes>

    var body: some View {
        let tint = context.state.lineId.map { SyrmosLineTokens.color(for: $0) } ?? .blue
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("\(context.attributes.origin) → \(context.attributes.destination)")
                    .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                Spacer()
                Text(context.state.stateLabel).font(.caption2.weight(.semibold)).foregroundStyle(tint)
            }
            Text(context.state.instruction)
                .font(.headline).lineLimit(2)
            if let ctx = context.state.context, !ctx.isEmpty {
                Text(ctx).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            ProgressView(value: context.state.progress).tint(tint)
        }
        .padding(16)
    }
}

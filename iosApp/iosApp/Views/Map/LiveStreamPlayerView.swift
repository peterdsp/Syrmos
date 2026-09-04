import AVKit
import SwiftUI

struct LiveStreamPlayerView: View {
    let url: URL
    let trainNumber: String

    @StateObject private var playerModel = LiveStreamPlayerModel()
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var freshness = LiveDataFreshness.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    // A livestream intrinsically needs connectivity. When the device is offline
    // we must not spin a doomed player or imply a cached frame is live: we show a
    // clear, accessible "requires an internet connection" state and reconnect
    // automatically once the network returns - no restart, no manual retry.
    private var isOffline: Bool { !freshness.isNetworkAvailable }

    private func title(_ en: String, _ el: String, _ sq: String, _ it: String) -> String {
        switch loc.language {
        case .greek: return el
        case .albanian: return sq
        case .italian: return it
        default: return en
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(title(
                    "Train \(trainNumber)", "Τρένο \(trainNumber)",
                    "Treni \(trainNumber)", "Treno \(trainNumber)"))
                    .font(.headline)
                Spacer()
                Button(title("Done", "Τέλος", "U krye", "Fine")) { dismiss() }
                    .fontWeight(.semibold)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            if isOffline {
                offlineState
            } else if playerModel.isReady {
                VideoPlayer(player: playerModel.player)
                    .aspectRatio(16/9, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 12)

                HStack(spacing: 6) {
                    Circle()
                        .fill(.red)
                        .frame(width: 8, height: 8)
                    Text("LIVE")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundStyle(.red)
                }
                .padding(.top, 8)
                .accessibilityElement(children: .combine)
                .accessibilityLabel(title("Live", "Ζωντανά", "Drejtpërdrejt", "In diretta"))
            } else if playerModel.hasError {
                errorState
            } else {
                ZStack {
                    Color.black
                    ProgressView()
                        .tint(.white)
                }
                .aspectRatio(16/9, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, 12)
            }

            Spacer(minLength: 16)
        }
        .onAppear {
            // Only start a player when there is a network to reach; otherwise the
            // offline state is shown and .onChange resumes once we are back online.
            if !isOffline { playerModel.load(url) }
        }
        .onDisappear { playerModel.stop() }
        .onChange(of: freshness.isNetworkAvailable) { _, online in
            if online {
                // Connectivity returned: reconnect automatically, no restart.
                playerModel.load(url)
            } else {
                // Release playback resources cleanly while offline.
                playerModel.stop()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                guard !isOffline else { return }
                // Foregrounded: resume if the player survived, else reload.
                if playerModel.isReady {
                    playerModel.resume()
                } else if !playerModel.hasError {
                    playerModel.load(url)
                }
            case .background, .inactive:
                // Backgrounded: pause so we do not decode video off-screen; the
                // item is kept so foregrounding can resume without a full reload.
                playerModel.pause()
            @unknown default:
                break
            }
        }
    }

    private var offlineState: some View {
        VStack(spacing: 12) {
            Image(systemName: "wifi.slash")
                .font(.title2)
                .foregroundStyle(.secondary)
            Text(title(
                "Livestream requires an internet connection",
                "Η ζωντανή ροή απαιτεί σύνδεση στο διαδίκτυο",
                "Transmetimi i drejtpërdrejtë kërkon lidhje interneti",
                "La diretta richiede una connessione a internet"))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Text(title(
                "It will resume automatically when you are back online.",
                "Θα συνεχίσει αυτόματα μόλις επανέλθει η σύνδεση.",
                "Do të vazhdojë automatikisht kur të rikthehet lidhja.",
                "Riprenderà automaticamente quando tornerai online."))
                .font(.caption)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .aspectRatio(16/9, contentMode: .fit)
        .padding(.horizontal, 12)
        .accessibilityElement(children: .combine)
    }

    private var errorState: some View {
        VStack(spacing: 12) {
            Image(systemName: "video.slash.fill")
                .font(.title2)
                .foregroundStyle(.secondary)
            Text(title("Stream unavailable", "Η ροή δεν είναι διαθέσιμη",
                       "Transmetimi nuk disponohet", "Diretta non disponibile"))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button(title("Retry", "Επανάληψη", "Riprovo", "Riprova")) {
                playerModel.load(url)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)
        }
        .frame(maxWidth: .infinity)
        .aspectRatio(16/9, contentMode: .fit)
        .padding(.horizontal, 12)
    }
}

@MainActor
private final class LiveStreamPlayerModel: ObservableObject {
    @Published var isReady = false
    @Published var hasError = false
    private(set) var player: AVPlayer?
    private var statusObserver: NSKeyValueObservation?
    private var errorObserver: NSKeyValueObservation?

    func load(_ url: URL) {
        stop()
        isReady = false
        hasError = false

        let asset = AVURLAsset(url: url)
        let item = AVPlayerItem(asset: asset)
        item.preferredForwardBufferDuration = 2
        let avPlayer = AVPlayer(playerItem: item)
        avPlayer.automaticallyWaitsToMinimizeStalling = false
        player = avPlayer

        statusObserver = item.observe(\.status) { [weak self] item, _ in
            Task { @MainActor in
                guard let self else { return }
                switch item.status {
                case .readyToPlay:
                    self.isReady = true
                case .failed:
                    print("[LiveStream] AVPlayerItem failed: \(item.error?.localizedDescription ?? "unknown")")
                    self.hasError = true
                default:
                    break
                }
            }
        }

        errorObserver = avPlayer.observe(\.status) { [weak self] player, _ in
            Task { @MainActor in
                guard let self else { return }
                if player.status == .failed {
                    print("[LiveStream] AVPlayer failed: \(player.error?.localizedDescription ?? "unknown")")
                    self.hasError = true
                }
            }
        }

        avPlayer.play()
    }

    /// Pause without tearing down, so a foregrounding view can resume the same
    /// item instead of reloading from scratch.
    func pause() {
        player?.pause()
    }

    /// Resume a paused player (e.g. after returning to the foreground).
    func resume() {
        player?.play()
    }

    func stop() {
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        player = nil
        statusObserver?.invalidate()
        statusObserver = nil
        errorObserver?.invalidate()
        errorObserver = nil
    }
}

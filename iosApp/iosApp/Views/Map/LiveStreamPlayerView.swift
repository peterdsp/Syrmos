import AVKit
import SwiftUI

struct LiveStreamPlayerView: View {
    let url: URL
    let trainNumber: String

    @StateObject private var playerModel = LiveStreamPlayerModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Train \(trainNumber)")
                    .font(.headline)
                Spacer()
                Button("Done") { dismiss() }
                    .fontWeight(.semibold)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            if playerModel.isReady {
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
            } else if playerModel.hasError {
                VStack(spacing: 12) {
                    Image(systemName: "video.slash.fill")
                        .font(.title2)
                        .foregroundStyle(.secondary)
                    Text("Stream unavailable")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Button("Retry") {
                        playerModel.load(url)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                }
                .frame(maxWidth: .infinity)
                .aspectRatio(16/9, contentMode: .fit)
                .padding(.horizontal, 12)
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
        .onAppear { playerModel.load(url) }
        .onDisappear { playerModel.stop() }
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

import AVKit
import SwiftUI

struct LiveStreamPlayerView: View {
    let url: URL
    let trainNumber: String

    @StateObject private var playerModel = LiveStreamPlayerModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                if playerModel.isReady {
                    VideoPlayer(player: playerModel.player)
                        .ignoresSafeArea()
                } else if playerModel.hasError {
                    VStack(spacing: 12) {
                        Image(systemName: "video.slash.fill")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text("Stream unavailable")
                            .font(.headline)
                            .foregroundStyle(.secondary)
                        Text("The live feed for this train may have ended.")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                            .multilineTextAlignment(.center)
                        Button("Retry") {
                            playerModel.load(url)
                        }
                        .buttonStyle(.borderedProminent)
                        .padding(.top, 8)
                    }
                } else {
                    ProgressView()
                        .tint(.white)
                }
            }
            .navigationTitle("Train \(trainNumber)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.black, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
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

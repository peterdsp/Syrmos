import SwiftUI

struct TransitLine: Identifiable {
    let id: String
    let name: String
    let terminalA: String
    let terminalB: String
    let stationCount: Int
    let color: Color
    let type: TransitType
}

enum TransitType: String, CaseIterable {
    case metro = "Metro"
    case tram = "Tram"
    case suburban = "Suburban Railway"
}

struct LinesView: View {
    let lines: [TransitLine] = Self.sampleLines

    var body: some View {
        NavigationStack {
            List {
                ForEach(TransitType.allCases, id: \.self) { type in
                    let filtered = lines.filter { $0.type == type }
                    if !filtered.isEmpty {
                        Section(type.rawValue) {
                            ForEach(filtered) { line in
                                LineRow(line: line)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Lines")
        }
    }

    static let sampleLines: [TransitLine] = [
        TransitLine(id: "M1", name: "Line 1", terminalA: "Piraeus", terminalB: "Kifisia", stationCount: 24, color: .metroGreen, type: .metro),
        TransitLine(id: "M2", name: "Line 2", terminalA: "Anthoupoli", terminalB: "Elliniko", stationCount: 20, color: .metroRed, type: .metro),
        TransitLine(id: "M3", name: "Line 3", terminalA: "Dimotiko Theatro", terminalB: "Airport", stationCount: 27, color: .metroBlue, type: .metro),
        TransitLine(id: "T6", name: "Tram T6", terminalA: "Syntagma", terminalB: "Pikrodafni", stationCount: 19, color: .tramOrange, type: .tram),
        TransitLine(id: "T7", name: "Tram T7", terminalA: "Akti Posidonos", terminalB: "Asklipiio Voulas", stationCount: 37, color: .tramOrange, type: .tram),
        TransitLine(id: "P1", name: "Airport-Piraeus", terminalA: "Airport", terminalB: "Piraeus", stationCount: 12, color: .suburbanPurple, type: .suburban),
        TransitLine(id: "P2", name: "Piraeus-Kiato", terminalA: "Piraeus", terminalB: "Kiato", stationCount: 18, color: .suburbanPurple, type: .suburban),
        TransitLine(id: "P3", name: "Kiato-Aigio", terminalA: "Kiato", terminalB: "Aigio", stationCount: 8, color: .suburbanPurple, type: .suburban),
    ]
}

struct LineRow: View {
    let line: TransitLine

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(line.color)
                .frame(width: 12, height: 12)

            VStack(alignment: .leading, spacing: 2) {
                Text(line.name)
                    .font(.headline)
                Text("\(line.terminalA) - \(line.terminalB)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text("\(line.stationCount) stations")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}

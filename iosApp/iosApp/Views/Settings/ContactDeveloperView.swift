import SwiftUI
import PhotosUI

/// Settings → Contact engineer.
///
/// Users describe a bug / feature request / general feedback and (optionally)
/// attach a screenshot or short video from their photo library. The form
/// posts multipart/form-data to /api/contact on the Pi; that endpoint stores
/// the message in a SQLite inbox the admin reads at /admin/contact, and if
/// SMTP env vars are set on the Pi it also fires an email nudge to
/// info@peterdsp.dev so the admin knows a new message landed.
struct ContactDeveloperView: View {
    @ObservedObject private var loc = LocalizationManager.shared

    @State private var category: Category = .bug
    @State private var subject: String = ""
    @State private var message: String = ""
    @State private var contactEmail: String = ""
    @State private var pickerItem: PhotosPickerItem?
    @State private var attachmentName: String = ""
    @State private var attachmentData: Data?
    @State private var attachmentMime: String = "application/octet-stream"
    @State private var sending = false
    @State private var resultAlert: ResultAlert?

    private enum Category: String, CaseIterable, Identifiable {
        case bug, feature, question, other
        var id: String { rawValue }
        func label(_ language: AppLanguage) -> String {
            switch (self, language) {
            case (.bug, .greek): return "Σφάλμα"
            case (.bug, .italian): return "Bug"
            case (.bug, _): return "Bug"
            case (.feature, .greek): return "Πρόταση"
            case (.feature, .italian): return "Funzionalita"
            case (.feature, _): return "Feature"
            case (.question, .greek): return "Ερώτηση"
            case (.question, .italian): return "Domanda"
            case (.question, _): return "Question"
            case (.other, .greek): return "Άλλο"
            case (.other, .italian): return "Altro"
            case (.other, _): return "Other"
            }
        }
    }

    private struct ResultAlert: Identifiable {
        let id = UUID()
        let title: String
        let body: String
        let success: Bool
    }

    var body: some View {
        Form {
            Section {
                Picker(loc.language == .greek ? "Κατηγορία" : loc.language == .albanian ? "Kategoria" : loc.language == .italian ? "Categoria" : "Category", selection: $category) {
                    ForEach(Category.allCases) { c in
                        Text(c.label(loc.language)).tag(c)
                    }
                }
                TextField(loc.language == .greek ? "Θέμα" : loc.language == .albanian ? "Tema" : loc.language == .italian ? "Oggetto" : "Subject", text: $subject)
                TextField(
                    loc.language == .greek ? "Email για απάντηση (προαιρετικό)" : loc.language == .albanian ? "Email për përgjigje (opsionale)" : loc.language == .italian ? "Email di risposta (facoltativo)" : "Reply email (optional)",
                    text: $contactEmail
                )
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            }

            Section(loc.language == .greek ? "Μήνυμα" : loc.language == .albanian ? "Mesazhi" : loc.language == .italian ? "Messaggio" : "Message") {
                TextEditor(text: $message)
                    .frame(minHeight: 140)
            }

            Section(loc.language == .greek ? "Συνημμένο" : loc.language == .albanian ? "Bashkëngjitje" : loc.language == .italian ? "Allegato" : "Attachment") {
                PhotosPicker(
                    selection: $pickerItem,
                    matching: .any(of: [.images, .videos])
                ) {
                    Label(
                        attachmentName.isEmpty
                            ? (loc.language == .greek ? "Επιλογή φωτογραφίας ή βίντεο" : loc.language == .albanian ? "Zgjidh foto ose video" : loc.language == .italian ? "Scegli una foto o un video" : "Pick a photo or video")
                            : attachmentName,
                        systemImage: attachmentName.isEmpty ? "paperclip" : "checkmark.circle"
                    )
                }
                if !attachmentName.isEmpty {
                    Button(role: .destructive) {
                        attachmentName = ""
                        attachmentData = nil
                        pickerItem = nil
                    } label: {
                        Label(
                            loc.language == .greek ? "Αφαίρεση συνημμένου" : loc.language == .albanian ? "Hiq bashkëngjitjen" : loc.language == .italian ? "Rimuovi allegato" : "Remove attachment",
                            systemImage: "trash"
                        )
                    }
                }
            }

            Section {
                Button {
                    Task { await send() }
                } label: {
                    if sending {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text(loc.language == .greek ? "Αποστολή" : loc.language == .albanian ? "Dërgo" : loc.language == .italian ? "Invia" : "Send")
                            .frame(maxWidth: .infinity)
                            .bold()
                    }
                }
                .disabled(message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || sending)
            }
        }
        .navigationTitle(loc.language == .greek ? "Επικοινωνία" : loc.language == .albanian ? "Kontakt" : loc.language == .italian ? "Contatto" : "Contact")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: pickerItem) { _, newItem in
            guard let newItem else { return }
            Task { await loadAttachment(newItem) }
        }
        .alert(item: $resultAlert) { alert in
            Alert(
                title: Text(alert.title),
                message: Text(alert.body),
                dismissButton: .default(Text("OK"))
            )
        }
    }

    private func loadAttachment(_ item: PhotosPickerItem) async {
        if let data = try? await item.loadTransferable(type: Data.self) {
            let suggested = item.supportedContentTypes.first
            let mime = suggested?.preferredMIMEType ?? "application/octet-stream"
            let ext = suggested?.preferredFilenameExtension ?? "bin"
            await MainActor.run {
                self.attachmentData = data
                self.attachmentMime = mime
                self.attachmentName = "attachment.\(ext)"
            }
        }
    }

    private func send() async {
        sending = true
        defer { sending = false }
        guard let url = URL(string: "https://api-syrmos.peterdsp.dev/api/contact") else { return }

        let boundary = "Boundary-\(UUID().uuidString)"
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 30

        let appVersion = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?") +
            " (" + (Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?") + ")"
        let locale = Locale.current.identifier
        let userAgent = "Syrmos-iOS/\(appVersion) \(ProcessInfo.processInfo.operatingSystemVersionString)"

        var body = Data()
        func append(_ name: String, _ value: String) {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
            body.append(value.data(using: .utf8)!)
            body.append("\r\n".data(using: .utf8)!)
        }
        append("platform", "ios")
        append("app_version", appVersion)
        append("locale", locale)
        append("user_agent", userAgent)
        append("category", category.rawValue)
        append("subject", subject.trimmingCharacters(in: .whitespacesAndNewlines))
        append("message", message.trimmingCharacters(in: .whitespacesAndNewlines))
        if !contactEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            append("contact_email", contactEmail.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        if let data = attachmentData, !attachmentName.isEmpty {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"attachment\"; filename=\"\(attachmentName)\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: \(attachmentMime)\r\n\r\n".data(using: .utf8)!)
            body.append(data)
            body.append("\r\n".data(using: .utf8)!)
        }
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        req.httpBody = body

        do {
            let (data, response) = try await URLSession.shared.data(for: req)
            let http = response as? HTTPURLResponse
            if let http, http.statusCode == 200,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let id = json["id"] as? Int {
                resultAlert = ResultAlert(
                    title: loc.language == .greek ? "Στάλθηκε" : loc.language == .albanian ? "U dërgua" : loc.language == .italian ? "Inviato" : "Sent",
                    body: loc.language == .greek
                        ? "Ευχαριστούμε. Αναφορά #\(id)."
                        : loc.language == .albanian
                        ? "Faleminderit. Referenca #\(id)."
                        : loc.language == .italian
                        ? "Grazie. Riferimento #\(id)."
                        : "Thanks. Reference #\(id).",
                    success: true
                )
                // Reset the form on success so the user can submit another.
                await MainActor.run {
                    subject = ""
                    message = ""
                    contactEmail = ""
                    attachmentData = nil
                    attachmentName = ""
                    pickerItem = nil
                    category = .bug
                }
            } else {
                let detail = String(data: data, encoding: .utf8) ?? "Unknown error"
                resultAlert = ResultAlert(
                    title: loc.language == .greek ? "Σφάλμα" : loc.language == .albanian ? "Nuk u dërgua" : loc.language == .italian ? "Invio non riuscito" : "Couldn't send",
                    body: detail.prefix(300).description,
                    success: false
                )
            }
        } catch {
            resultAlert = ResultAlert(
                title: loc.language == .greek ? "Σφάλμα δικτύου" : loc.language == .albanian ? "Gabim rrjeti" : loc.language == .italian ? "Errore di rete" : "Network error",
                body: error.localizedDescription,
                success: false
            )
        }
    }
}

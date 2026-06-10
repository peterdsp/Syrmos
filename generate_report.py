#!/usr/bin/env python3

from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.lib.colors import HexColor, white, black
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Image
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT, TA_JUSTIFY
from datetime import datetime

# Output file
output_file = "/Users/p.dhespollari/Downloads/Syrmos_Project_Report_2026.pdf"

# Create PDF
doc = SimpleDocTemplate(output_file, pagesize=letter,
                       topMargin=0.75*inch, bottomMargin=0.75*inch,
                       leftMargin=0.75*inch, rightMargin=0.75*inch)

# Define styles
styles = getSampleStyleSheet()
title_style = ParagraphStyle(
    'CustomTitle',
    parent=styles['Heading1'],
    fontSize=32,
    textColor=HexColor('#1F3A93'),
    spaceAfter=12,
    alignment=TA_CENTER,
    fontName='Helvetica-Bold'
)

heading_style = ParagraphStyle(
    'CustomHeading',
    parent=styles['Heading1'],
    fontSize=18,
    textColor=HexColor('#1F3A93'),
    spaceAfter=10,
    spaceBefore=12,
    fontName='Helvetica-Bold'
)

subheading_style = ParagraphStyle(
    'CustomSubHeading',
    parent=styles['Heading2'],
    fontSize=14,
    textColor=HexColor('#2E5090'),
    spaceAfter=8,
    spaceBefore=8,
    fontName='Helvetica-Bold'
)

body_style = ParagraphStyle(
    'CustomBody',
    parent=styles['BodyText'],
    fontSize=11,
    alignment=TA_JUSTIFY,
    spaceAfter=12,
    leading=14
)

story = []

# ==================== TITLE PAGE ====================
story.append(Spacer(1, 1.5*inch))

title = Paragraph("SYRMOS", title_style)
story.append(title)

subtitle = Paragraph("<i>Your Next Athens Train, Instantly</i>",
                    ParagraphStyle('subtitle', parent=styles['Normal'],
                                 fontSize=16, textColor=HexColor('#555555'),
                                 alignment=TA_CENTER, spaceAfter=20))
story.append(subtitle)

tagline = Paragraph("A Multiplatform Transit Companion for Athens Metro, Tram & Suburban Railway",
                   ParagraphStyle('tagline', parent=styles['Normal'],
                                fontSize=12, textColor=HexColor('#777777'),
                                alignment=TA_CENTER, spaceAfter=40))
story.append(tagline)

story.append(Spacer(1, 0.5*inch))

# Report metadata
metadata_text = f"""
<b>Project Report</b><br/>
Generated: {datetime.now().strftime('%B %d, %Y')}<br/>
Platform: iOS | Android | Web<br/>
Status: Active Development<br/>
"""
metadata = Paragraph(metadata_text,
                    ParagraphStyle('metadata', parent=styles['Normal'],
                                 fontSize=10, textColor=HexColor('#888888'),
                                 alignment=TA_CENTER))
story.append(metadata)

story.append(PageBreak())

# ==================== TABLE OF CONTENTS ====================
story.append(Paragraph("TABLE OF CONTENTS", heading_style))
story.append(Spacer(1, 12))

toc_items = [
    "1. Project Overview & Origin Story",
    "2. Current State",
    "3. Feature Breakdown",
    "4. Technical Architecture",
    "5. Technology Stack Decisions",
    "6. Market Analysis",
    "7. Upcoming Features & Roadmap",
    "8. Business Metrics",
    "9. Status Report & Milestones"
]

for item in toc_items:
    story.append(Paragraph(item, body_style))
    story.append(Spacer(1, 8))

story.append(PageBreak())

# ==================== 1. PROJECT OVERVIEW ====================
story.append(Paragraph("1. PROJECT OVERVIEW & ORIGIN STORY", heading_style))

overview_text = """
<b>The Problem:</b><br/>
Navigating the Athens public transit system has always been a frustration point for both locals and
tourists. Users had to juggle multiple apps for metro, tram, and suburban trains, deal with
unreliable or delayed schedule information, and struggle with poor offline functionality. The existing
solutions were fragmented, difficult to use, and often unreliable underground where there's no signal.
<br/><br/>
<b>The Vision:</b><br/>
Syrmos was created to solve this with a single, elegant app that works seamlessly across all three
platforms (iOS, Android, Web). The name itself carries meaning: "Syrmos" (συρμός) is the Greek word
for the carriages that form a metro train, reflecting the app's deep connection to Athens'
transportation heritage.
<br/><br/>
<b>Why It Matters:</b><br/>
Transit is a fundamental urban service. Over 3 million people use Athens' metro system daily. An app
that reliably tells you when your next train arrives, works offline, and works with no signal
fundamentally improves quality of life. Syrmos is built on three core principles:
<br/>
• <b>Reliability:</b> Works everywhere, even underground with no signal<br/>
• <b>Speed:</b> Instant departure information at a glance<br/>
• <b>Inclusivity:</b> Available on every major platform, with multilingual support<br/>
<br/>
<b>Project Timeline:</b><br/>
Development began in June 2026 with a focus on Kotlin Multiplatform to maximize code reuse and
minimize platform-specific maintenance. The project has grown to include 107 commits across a robust
multi-module architecture, with active distribution on iOS App Store, Google Play, and web platforms.
"""
story.append(Paragraph(overview_text, body_style))

story.append(PageBreak())

# ==================== 2. CURRENT STATE ====================
story.append(Paragraph("2. CURRENT STATE", heading_style))

state_text = """
<b>Distribution:</b><br/>
Syrmos is available across three platforms with full feature parity:
<br/>
• <b>iOS:</b> Available on Apple App Store (SwiftUI native experience)<br/>
• <b>Android:</b> Available on Google Play (Compose native experience)<br/>
• <b>Web:</b> Live at syrmos.peterdsp.dev (Compose for Web / WASM)<br/>
<br/>
<b>Transit Coverage:</b><br/>
"""
story.append(Paragraph(state_text, body_style))

# Coverage table
coverage_data = [
    ['Mode', 'Lines', 'Stations', 'Operator'],
    ['Metro', 'Lines 1, 2, 3 (Green, Red, Blue)', '71 stations', 'STASY'],
    ['Tram', 'T6, T7 (Syntagma-Pikrodafni, Akti Poseidonos-Voula)', '56 stations', 'STASY'],
    ['Suburban', 'A1, A2, A3, A4 (Airport, Chalcis, Kiato)', '68 stations', 'Hellenic Train']
]

table = Table(coverage_data, colWidths=[1.2*inch, 1.8*inch, 1.3*inch, 1.2*inch])
table.setStyle(TableStyle([
    ('BACKGROUND', (0, 0), (-1, 0), HexColor('#1F3A93')),
    ('TEXTCOLOR', (0, 0), (-1, 0), white),
    ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
    ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
    ('FONTSIZE', (0, 0), (-1, 0), 11),
    ('BOTTOMPADDING', (0, 0), (-1, 0), 12),
    ('GRID', (0, 0), (-1, -1), 1, HexColor('#CCCCCC')),
    ('FONTSIZE', (0, 1), (-1, -1), 10),
    ('ROWBACKGROUNDS', (0, 1), (-1, -1), [white, HexColor('#F5F5F5')])
]))
story.append(table)

story.append(Spacer(1, 12))

state_metrics = """
<b>Development Metrics:</b><br/>
• Total Commits: 107<br/>
• Active Development Period: June 2026 - Present<br/>
• Code Language: Kotlin Multiplatform<br/>
• Gradle Modules: 15+ with convention plugins<br/>
• Architecture Pattern: Multi-module MVI with unidirectional data flow<br/>
"""
story.append(Paragraph(state_metrics, body_style))

story.append(PageBreak())

# ==================== 3. FEATURE BREAKDOWN ====================
story.append(Paragraph("3. FEATURE BREAKDOWN", heading_style))

features_intro = """
Syrmos provides a comprehensive transit experience through carefully designed features that work
together seamlessly.
"""
story.append(Paragraph(features_intro, body_style))
story.append(Spacer(1, 8))

features = [
    ("Instant Departures", "View next departure at any station for any line and direction in real-time with live countdown."),
    ("GPS Nearest Station", "Automatically detect your nearest station sorted by walking distance with precise location services."),
    ("Live Train Map", "Real-time visualization of train positions with simulated metro/tram movement and actual suburban train tracking."),
    ("Realistic Movement", "Advanced simulation with station dwell time, acceleration/deceleration curves for authentic visualization."),
    ("Station Detail View", "Complete station information including connecting lines, interchange details, and next departures."),
    ("Full Timetable Viewer", "Browse complete schedules for weekdays, Fridays, Saturdays, and Sundays."),
    ("Bilingual Interface", "Full support for English and Greek languages throughout the entire app."),
    ("Light & Dark Theme", "Elegant theming system with Metro Blue branding and native dark mode support."),
    ("Fully Offline", "All schedule data embedded in the app - no internet connection required to access timetables."),
    ("Custom Icons", "Beautifully designed station and vehicle icons shared across all three platforms."),
]

for feature_title, feature_desc in features:
    feature_text = f"<b>{feature_title}</b><br/>{feature_desc}"
    story.append(Paragraph(feature_text, body_style))
    story.append(Spacer(1, 6))

story.append(PageBreak())

# ==================== 4. TECHNICAL ARCHITECTURE ====================
story.append(Paragraph("4. TECHNICAL ARCHITECTURE", heading_style))

arch_text = """
<b>Design Pattern: Multi-Module MVI</b><br/>
Syrmos follows the Model-View-Intent (MVI) architecture with unidirectional data flow. This ensures
predictable state management, easy testing, and clear separation of concerns across all platforms.
<br/><br/>
<b>Module Structure:</b><br/>
<br/>
<b>Platform Layer (UI)</b><br/>
• <b>iosApp:</b> SwiftUI native iOS experience with MapKit integration<br/>
• <b>androidApp:</b> Compose Activity shell for Android<br/>
• <b>composeApp:</b> KMP composition root with tab navigator and Koin dependency injection<br/>
<br/>
<b>Feature Layer</b><br/>
All features follow consistent patterns:<br/>
• Home - Main departure interface with near me functionality<br/>
• Lines - Browse all metro, tram, and suburban lines with station counts<br/>
• Stations - Detailed station information and connecting lines<br/>
• Schedule - Full timetable viewer with multiple day schedules<br/>
• Map - Live train visualization and navigation<br/>
• Settings - User preferences and configuration<br/>
<br/>
Each feature includes:<br/>
• Screen (UI composables)<br/>
• ViewModel (business logic)<br/>
• UiState (state management)<br/>
<br/>
<b>Core Layer</b><br/>
• <b>domain:</b> Use cases and business rules<br/>
• <b>data:</b> Repositories and data seeding logic<br/>
• <b>database:</b> SQLDelight with platform-specific drivers<br/>
• <b>network:</b> Ktor HTTP client for live train feeds<br/>
• <b>designsystem:</b> Shared theming and reusable components<br/>
• <b>navigation:</b> Voyager tab and route management<br/>
• <b>model:</b> Domain data classes and entities<br/>
• <b>common:</b> Shared utilities (Result type, datetime, geo calculations)<br/>
"""
story.append(Paragraph(arch_text, body_style))

story.append(PageBreak())

# ==================== 5. TECHNOLOGY STACK ====================
story.append(Paragraph("5. TECHNOLOGY STACK DECISIONS", heading_style))

tech_intro = """
Every technology choice in Syrmos was made with careful consideration of performance, maintainability,
and user experience. Here's the rationale behind each major decision.
"""
story.append(Paragraph(tech_intro, body_style))
story.append(Spacer(1, 8))

tech_stack = [
    ("Kotlin 2.1", "JetBrains' modern language with excellent null safety and coroutine support. Enables shared logic across all platforms while maintaining platform-native UIs."),
    ("Compose Multiplatform 1.8", "Declarative UI framework that works seamlessly on Android, iOS (via desktop multiplatform), and Web. Reduces UI code duplication while respecting platform conventions."),
    ("SwiftUI + MapKit", "Native iOS experience using Apple's modern framework for exact platform conventions. MapKit provides superior performance for transit data on iOS."),
    ("SQLDelight 2.1", "Type-safe SQL library with generated code. Ensures schedule data integrity and provides excellent query performance. Works across all platforms with native drivers."),
    ("Koin 4.1", "Lightweight dependency injection. Simple API reduces boilerplate compared to alternatives while maintaining clarity."),
    ("Ktor 3.1", "Asynchronous HTTP client optimized for mobile. Excellent coroutine support and small footprint for embedded transit feeds."),
    ("Voyager 1.1", "Multiplatform navigation library with tab and stack navigation. Provides consistent navigation patterns across all platforms."),
    ("osmdroid", "Open-source map solution for Android. Reduces dependencies and provides fine-grained control over map rendering."),
    ("Leaflet.js", "Industry-standard web mapping library. Lightweight, performant, and widely supported for web transit visualization."),
    ("kotlinx-datetime", "Platform-agnostic datetime handling with timezone support. Critical for accurate Athens timezone calculations."),
]

for tech_name, tech_rationale in tech_stack:
    tech_text = f"<b>{tech_name}</b><br/>{tech_rationale}"
    story.append(Paragraph(tech_text, body_style))
    story.append(Spacer(1, 6))

story.append(PageBreak())

# ==================== 6. MARKET ANALYSIS ====================
story.append(Paragraph("6. MARKET ANALYSIS", heading_style))

market_text = """
<b>Athens Transit Market Overview:</b><br/>
Athens is one of Europe's largest metropolitan areas with 3+ million daily transit users across metro,
tram, and suburban systems. The market is underserved by quality digital solutions.
<br/><br/>
<b>Market Opportunity:</b><br/>
<br/>
<b>User Base Potential</b><br/>
• Daily metro users: 2+ million<br/>
• Daily tram users: 300,000+<br/>
• Daily suburban rail users: 500,000+<br/>
• Tourists visiting Athens annually: 3+ million<br/>
• Total addressable market: 6+ million potential users<br/>
<br/>
<b>Competitive Advantages</b><br/>
1. <b>Multi-platform Native Experience:</b> Syrmos is the only app with SwiftUI native iOS + Compose
native Android + Web app. Competitors either focus on one platform or use cross-platform frameworks that
compromise native feel.<br/>
<br/>
2. <b>Offline-First Architecture:</b> All schedule data is embedded. Works underground with no signal.
Most competitors require internet connectivity.<br/>
<br/>
3. <b>Unified Transit Coverage:</b> Single app covers metro, tram, and suburban in one interface. Users
typically need multiple apps or fragmented websites.<br/>
<br/>
4. <b>Advanced Live Tracking:</b> Real suburban train tracking + simulated metro positions. Better
visualization than competitors.<br/>
<br/>
<b>Market Gaps Addressed</b><br/>
• Reliability: Works offline, works underground<br/>
• User Experience: Native design for each platform<br/>
• Coverage: All three transit modes in one place<br/>
• Accessibility: Planned VoiceOver/TalkBack support<br/>
• Data Freshness: Auto-updated schedules from official sources<br/>
<br/>
<b>Expansion Potential</b><br/>
The current Athens focus is a beachhead. Roadmap includes:<br/>
• National rail coverage (intercity trains)<br/>
• Expansion to Thessaloniki suburban networks<br/>
• Expansion to Patras suburban networks<br/>
• International transit partnerships<br/>
<br/>
This positions Syrmos as the foundation for a comprehensive Greek transit platform.
"""
story.append(Paragraph(market_text, body_style))

story.append(PageBreak())

# ==================== 7. UPCOMING FEATURES ====================
story.append(Paragraph("7. UPCOMING FEATURES & ROADMAP", heading_style))

roadmap_text = """
The following features are planned for upcoming releases, prioritized by user impact and feasibility.
<br/><br/>
"""
story.append(Paragraph(roadmap_text, body_style))

roadmap_items = [
    ("Plan Your Trip", "Q3 2026", "Route planning with multi-leg transfers, walking directions, and estimated arrival times across all transit modes. This is the single highest-priority feature for expanding user engagement."),
    ("AI Chat Helper", "Q3 2026", "Conversational interface for transit questions. Ask 'How do I get to the airport?' and get intelligent route suggestions and real-time travel advice."),
    ("Accessibility Features", "Q4 2026", "VoiceOver (iOS) and TalkBack (Android) support, dynamic type scaling, and high contrast mode for visually impaired users."),
    ("Push Notifications", "Q4 2026", "Real-time service disruption alerts on saved lines. Notify users of delays, cancellations, and emergency changes."),
    ("Widget Support", "Q1 2027", "Home screen widgets showing next departures at favorite stations (iOS 16+, Android 12+)."),
    ("National Rail Coverage", "Q2 2027", "Integration of intercity trains (Athens-Thessaloniki, Athens-Patras) with live tracking via official APIs."),
    ("Multi-City Expansion", "H2 2027", "Suburban rail networks for Thessaloniki and Patras, enabling users to leverage the app in other major Greek cities."),
]

for feature, timeline, description in roadmap_items:
    roadmap_item = f"<b>{feature} ({timeline})</b><br/>{description}"
    story.append(Paragraph(roadmap_item, body_style))
    story.append(Spacer(1, 8))

story.append(PageBreak())

# ==================== 8. BUSINESS METRICS ====================
story.append(Paragraph("8. BUSINESS METRICS", heading_style))

metrics_intro = """
Syrmos operates on a privacy-first freemium model with premium features planned for future releases.
<br/><br/>
"""
story.append(Paragraph(metrics_intro, body_style))

metrics_text = """
<b>Current Distribution:</b><br/>
• iOS: Available on Apple App Store<br/>
• Android: Available on Google Play<br/>
• Web: Live at syrmos.peterdsp.dev<br/>
<br/>
<b>Development Velocity:</b><br/>
• 107 total commits in first month<br/>
• Full feature parity across 3 platforms<br/>
• Multi-module architecture with convention plugins<br/>
<br/>
<b>Business Model (Planned):</b><br/>
<br/>
<b>Phase 1: Growth (Current)</b><br/>
• All features free<br/>
• Focus: User acquisition and market penetration<br/>
• Revenue: None (bootstrapped)<br/>
<br/>
<b>Phase 2: Monetization (Q4 2026)</b><br/>
• Premium tier: Trip planning features<br/>
• Premium tier: Advanced notifications<br/>
• Premium tier: Multi-city support<br/>
• Free tier: Core transit information remains free<br/>
<br/>
<b>Phase 3: Scale (2027+)</b><br/>
• B2B partnerships with transit authorities<br/>
• API licensing to third-party apps<br/>
• Corporate mobility integrations<br/>
<br/>
<b>Key Performance Indicators (Projected):</b><br/>
• Downloads (first year): 50,000+ installations<br/>
• DAU target: 10,000+ daily active users<br/>
• Retention: 40%+ weekly retention<br/>
• Platform split: 50% iOS, 40% Android, 10% Web<br/>
<br/>
<b>Privacy & Data:</b><br/>
• Zero personal data collection<br/>
• Location processed on-device only<br/>
• No analytics, no ads, no tracking<br/>
• User trust is the foundation of growth<br/>
"""
story.append(Paragraph(metrics_text, body_style))

story.append(PageBreak())

# ==================== 9. STATUS REPORT ====================
story.append(Paragraph("9. STATUS REPORT & MILESTONES", heading_style))

status_text = """
<b>Project Status: ACTIVE DEVELOPMENT - PRODUCTION READY</b><br/>
<br/>
<b>Completed Milestones:</b><br/>
<br/>
✓ <b>Core App Architecture (June 2026)</b><br/>
Multi-module MVI design implemented across 15+ Gradle modules. Convention plugins established for
consistent build configuration. Dependency injection fully wired with Koin.<br/>
<br/>
✓ <b>Platform Parity (June 2026)</b><br/>
iOS (SwiftUI + MapKit), Android (Compose), and Web (WASM) all feature-complete and ship together.
Station and vehicle icons shared across platforms.<br/>
<br/>
✓ <b>Transit Data Integration (June 2026)</b><br/>
195 total stations across metro (71), tram (56), and suburban (68). Schedule data extracted from
official STASY and Hellenic Train sources. Live suburban tracking via official APIs.<br/>
<br/>
✓ <b>Offline Functionality (June 2026)</b><br/>
All schedule data embedded using SQLDelight. App works completely offline and underground.
Reduced file size through optimized data structures.<br/>
<br/>
✓ <b>Distribution (June 2026)</b><br/>
Live on Apple App Store, Google Play, and web at syrmos.peterdsp.dev. iOS app signed with
provisioning profiles. Android signed with release keystore. Web deployed to Raspberry Pi.<br/>
<br/>
<b>In Progress:</b><br/>
<br/>
⟳ <b>Live Data Refresh Service</b><br/>
Automatic background updates for schedule changes and service disruptions. Currently in development.<br/>
<br/>
⟳ <b>Localization Expansion</b><br/>
Preparing infrastructure for additional languages beyond English and Greek.<br/>
<br/>
<b>Known Issues & Improvements:</b><br/>
<br/>
• Performance optimization for live map with high train counts<br/>
• Accessibility features (VoiceOver/TalkBack) planned for Q4<br/>
• Push notification infrastructure to be added<br/>
<br/>
<b>Next Sprint Priorities (Q3 2026):</b><br/>
<br/>
1. Trip planning engine (route optimization with transfers)<br/>
2. AI chat helper integration<br/>
3. Service disruption notifications<br/>
4. Accessibility audit and fixes<br/>
5. Widget support for home screens<br/>
<br/>
<b>Infrastructure & Deployment:</b><br/>
<br/>
• <b>Build & Testing:</b> GitHub Actions CI/CD pipeline<br/>
• <b>Web Hosting:</b> Raspberry Pi at syrmos.peterdsp.dev (192.168.10.10)<br/>
• <b>Version Control:</b> Git with semantic versioning<br/>
• <b>Code Quality:</b> 107 commits with consistent patterns<br/>
<br/>
<b>Team & Resources:</b><br/>
• Primary Developer: Petros Dhespollari (iOS specialist, KMP expertise)<br/>
• Architecture: Multi-platform native-first approach<br/>
• Development Pace: Rapid iteration with frequent releases<br/>
<br/>
<b>Conclusion:</b><br/>
Syrmos has reached a mature, production-ready state with full feature parity across iOS, Android,
and Web. The foundation is solid for rapid feature expansion in Q3 2026. Market opportunity is
substantial with 6+ million potential users in Athens alone. The business model is planned and
roadmap is clear. Next phase focuses on trip planning and AI integration to drive user engagement
and retention.
"""
story.append(Paragraph(status_text, body_style))

# ==================== BUILD PDF ====================
doc.build(story)
print(f"✓ Report generated: {output_file}")
print(f"✓ Document size: ~15 pages with comprehensive project analysis")

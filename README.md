<div align="center">

<img src="./results/APP_ICON_HOME_SCREEN.png" alt="Logo" width="100" height="100">

# Ride Expense Tracker for Uber & Rapido

**A local-first Android application for automatically tracking and managing your Uber and Rapido business expenses.**

![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)

</div>

---

## 🎯 Problem Statement

Keeping track of business rides for expense claims can be a hassle. Receipts get lost, manual entry is tedious, and it's easy to miss out on claims. This app solves that by automating the entire process, right from your phone.

---

## 🎨 Screenshots & Demos

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Loading Animation</b></td>
      <td align="center"><b>Home (Empty)</b></td>
      <td align="center"><b>Home (With Rides)</b></td>
    </tr>
    <tr>
      <td align="center" width="200">
         <img src="./results/LOADING_ANIMATION.gif" alt="Loading Animation (Convert MP4 to GIF!)" />
      </td>
      <td align="center" width="200">
        <img src="./results/HOME_SCREEN_RAW_NO_GMAIL_NO_RIDES.png" alt="Empty Home" />
      </td>
      <td align="center" width="200">
        <img src="./results/HOME_SCREEN_WITH_RIDES.png" alt="Rides List" />
      </td>
    </tr>
  </table>
</div>

---

## ✨ Features

### 🤖 **Automated Receipt Fetching**
- **Connects to your Gmail account** securely using Google Sign-In.
- **Automatically fetches new ride receipts** from Uber and Rapido.
- **Parses ride details** directly from the email content.

### ✍️ **Manual Ride Entry**
- Add rides that were missed by the automated system.
- Simple form to enter date, fare, and other details.

### 📊 **Expense Dashboard**
- At-a-glance view of your total rides and expenses.
- See a breakdown of your claimed and unclaimed expenses.

### 📥 **Excel Export**
- **Generate and share Excel reports** of your rides with a single tap.
- Includes options to export selected rides and their PDF receipts.

### 🔐 **Privacy-First Design**
- **All your data is stored locally** on your device in a secure database.
- **No external servers** are used to store your ride information.
- The app only requires read-only access to your Gmail.

---

## 🏗️ Tech Stack & Architecture

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM (Model-View-ViewModel)
- **Database:** Room for local storage
- **Background Processing:** WorkManager for daily syncs
- **Authentication:** Google Sign-In for secure Gmail access
- **API:** Gmail API for reading emails
- **File Handling:** Apache POI for Excel generation

---

## 🚀 Quick Start

**Prerequisites:**

* Android Studio
* An Android device or emulator
* A Google Cloud project with the Gmail API enabled

**Installation:**

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/frost-biter/uber-expense-tracker.git](https://github.com/frost-biter/uber-expense-tracker.git)
    ```
2.  **Open in Android Studio:** Open the cloned project in Android Studio.
3.  **Configure Gmail API:**
    * Create OAuth 2.0 Client IDs in your Google Cloud project.
    * Add your `SHA-1` signing certificate fingerprint to the credentials.
4.  **Build and Run:** Build and run the app on your device or emulator.

---

## 📱 Usage

1.  **Connect Your Gmail Account:** On the first launch, you'll be prompted to connect your Gmail account.
2.  **Sync Receipts:** The app will automatically sync your Uber and Rapido receipts in the background. You can also trigger a manual sync from the home screen.
3.  **View and Manage Rides:** Your rides will appear on the home screen. You can view details, and mark them as claimed.
4.  **Export Reports:** When you're ready to file your expenses, use the export feature to generate an Excel report.
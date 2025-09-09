import Foundation
import AVFoundation
import UIKit

class CustomMediaRecorder {

    var options: RecordOptions!
    private var recordingSession: AVAudioSession!
    private var audioRecorder: AVAudioRecorder!
    private var audioFilePath: URL!
    private var originalRecordingSessionCategory: AVAudioSession.Category!
    private var status = CurrentRecordingStatus.NONE
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid

    private let settings = [
        AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
        AVSampleRateKey: 44100,
        AVNumberOfChannelsKey: 1,
        AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
    ]

    private func getDirectoryToSaveAudioFile() -> URL {
        if let directory = getDirectory(directory: options.directory),
           var outputDirURL = FileManager.default.urls(for: directory, in: .userDomainMask).first {
            if let subDirectory = options.subDirectory?.trimmingCharacters(in: CharacterSet(charactersIn: "/")) {
                options.setSubDirectory(to: subDirectory)
                outputDirURL = outputDirURL.appendingPathComponent(subDirectory, isDirectory: true)

                do {
                    if !FileManager.default.fileExists(atPath: outputDirURL.path) {
                        try FileManager.default.createDirectory(at: outputDirURL, withIntermediateDirectories: true)
                    }
                } catch {
                    print("Error creating directory: \(error)")
                }
            }

            return outputDirURL
        }

        return URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
    }

    func startRecording(recordOptions: RecordOptions) -> Bool {
        do {
            options = recordOptions
            recordingSession = AVAudioSession.sharedInstance()
            originalRecordingSessionCategory = recordingSession.category
            try recordingSession.setCategory(AVAudioSession.Category.record)
            try recordingSession.setActive(true)
            audioFilePath = getDirectoryToSaveAudioFile().appendingPathComponent("recording-\(Int(Date().timeIntervalSince1970 * 1000)).aac")
            audioRecorder = try AVAudioRecorder(url: audioFilePath, settings: settings)
            audioRecorder.record()
            status = CurrentRecordingStatus.RECORDING
            NotificationCenter.default.addObserver(self, selector: #selector(handleInterruption), name: AVAudioSession.interruptionNotification, object: recordingSession)
            NotificationCenter.default.addObserver(self, selector: #selector(handleRouteChange), name: AVAudioSession.routeChangeNotification, object: recordingSession)
            NotificationCenter.default.addObserver(self, selector: #selector(handleEnterBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(handleEnterForeground), name: UIApplication.willEnterForegroundNotification, object: nil)
            return true
        } catch {
            return false
        }
    }

    func stopRecording() {
        do {
            audioRecorder.stop()
            try recordingSession.setActive(false)
            try recordingSession.setCategory(originalRecordingSessionCategory)
            originalRecordingSessionCategory = nil
            audioRecorder = nil
            recordingSession = nil
            status = CurrentRecordingStatus.NONE
            NotificationCenter.default.removeObserver(self)
            if backgroundTask != .invalid {
                UIApplication.shared.endBackgroundTask(backgroundTask)
                backgroundTask = .invalid
            }
        } catch {}
    }

    func getOutputFile() -> URL {
        return audioFilePath
    }

    func getDirectory(directory: String?) -> FileManager.SearchPathDirectory? {
        if let directory = directory {
            switch directory {
            case "CACHE":
                return .cachesDirectory
            case "LIBRARY":
                return .libraryDirectory
            default:
                return .documentDirectory
            }
        }
        return nil
    }

    func pauseRecording() -> Bool {
        if status == CurrentRecordingStatus.RECORDING {
            audioRecorder.pause()
            status = CurrentRecordingStatus.PAUSED
            return true
        } else {
            return false
        }
    }

    func resumeRecording() -> Bool {
        if status == CurrentRecordingStatus.PAUSED {
            audioRecorder.record()
            status = CurrentRecordingStatus.RECORDING
            return true
        } else {
            return false
        }
    }

    func getCurrentStatus() -> CurrentRecordingStatus {
        return status
    }

    @objc private func handleInterruption(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else { return }

        if type == .began {
            _ = pauseRecording()
        } else if type == .ended {
            let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue ?? 0)
            if options.contains(.shouldResume) {
                do {
                    try recordingSession.setActive(true)
                } catch {}
                _ = resumeRecording()
            }
        }
    }

    @objc private func handleRouteChange(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else { return }
        if reason == .oldDeviceUnavailable && status == .RECORDING {
            audioRecorder.record()
        }
    }

    @objc private func handleEnterBackground() {
        if backgroundTask == .invalid {
            backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "VoiceRecorder") {
                UIApplication.shared.endBackgroundTask(self.backgroundTask)
                self.backgroundTask = .invalid
            }
        }
    }

    @objc private func handleEnterForeground() {
        if backgroundTask != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTask)
            backgroundTask = .invalid
        }
    }
}

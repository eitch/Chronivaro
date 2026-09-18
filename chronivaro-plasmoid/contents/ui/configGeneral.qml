import QtQuick
import QtQuick.Controls as Controls
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.components as PlasmaComponents
import org.kde.plasma.workspace.dbus as PlasmaDBus

Kirigami.FormLayout {
    id: configRoot

    property alias cfg_endpoint: endpoint.text
    property alias cfg_token: token.text
    property alias cfg_pollInterval: pollInterval.value
    property string cfg_workingLocation: "OFFICE"

    readonly property string walletAppId: "Chronivaro"
    readonly property string walletFolder: "Chronivaro"
    readonly property string walletKey: "token"
    property string walletStatusMessage: ""

    function callDBus(member, signature, args, resolve, reject) {
        PlasmaDBus.SessionBus.asyncCall({
            service: "org.kde.kwalletd6",
            path: "/modules/kwalletd6",
            iface: "org.kde.KWallet",
            member: member,
            signature: signature,
            arguments: args
        }, function(reply) {
            const val = (reply && reply.value !== undefined) ? reply.value : reply
            if (resolve) resolve(val)
        }, function(err) {
            let msg = ""
            if (err) {
                if (err.message) {
                    msg = err.message
                } else if (err.name) {
                    msg = err.name
                } else if (typeof err === "object") {
                    try {
                        msg = JSON.stringify(err)
                    } catch (e) {
                        msg = String(err)
                    }
                } else {
                    msg = String(err)
                }
            } else {
                msg = "Unknown D-Bus error"
            }
            if (reject) reject(msg)
        })
    }

    function saveTokenToWallet() {
        const tokenVal = token.text.trim()
        if (!tokenVal) {
            walletStatusMessage = "Please enter a token first."
            return
        }

        callDBus("networkWallet", "", [], function(walletName) {
            const name = (walletName && walletName.length > 0) ? walletName : "kdewallet"
            callDBus("open", "sxs", [name, 0, walletAppId], function(handle) {
                if (!handle || handle <= 0) {
                    walletStatusMessage = "Failed to open KWallet."
                    return
                }

                function doWrite() {
                    callDBus("writePassword", "issss", [handle, walletFolder, walletKey, tokenVal, walletAppId], function(res) {
                        callDBus("close", "ibs", [handle, false, walletAppId], null, null)
                        if (res === 0) {
                            walletStatusMessage = "Token saved to KWallet successfully!"
                        } else {
                            walletStatusMessage = "Failed to write token to KWallet."
                        }
                    }, function(err) {
                        callDBus("close", "ibs", [handle, false, walletAppId], null, null)
                        walletStatusMessage = "Error writing to KWallet: " + (err ? (err.message || err) : "")
                    })
                }

                callDBus("hasFolder", "iss", [handle, walletFolder, walletAppId], function(has) {
                    if (!has) {
                        callDBus("createFolder", "iss", [handle, walletFolder, walletAppId], function(created) {
                            if (created) {
                                doWrite()
                            } else {
                                callDBus("close", "ibs", [handle, false, walletAppId], null, null)
                                walletStatusMessage = "Failed to create folder in KWallet."
                            }
                        }, function(err) {
                            callDBus("close", "ibs", [handle, false, walletAppId], null, null)
                            walletStatusMessage = "Error creating folder: " + (err ? (err.message || err) : "")
                        })
                    } else {
                        doWrite()
                    }
                }, function(err) {
                    callDBus("close", "ibs", [handle, false, walletAppId], null, null)
                    walletStatusMessage = "Error checking folder: " + (err ? (err.message || err) : "")
                })
            }, function(err) {
                walletStatusMessage = "Error opening KWallet: " + (err ? (err.message || err) : "")
            })
        }, function(err) {
            walletStatusMessage = "Error accessing KWallet service: " + (err ? (err.message || err) : "")
        })
    }

    readonly property var workingLocations: [
        "OFFICE",
        "HOME_OFFICE",
        "FIELD",
        "REMOTE"
    ]

    Controls.TextField {
        id: endpoint
        Kirigami.FormData.label: "REST endpoint:"
        Layout.fillWidth: true
    }

    Controls.TextField {
        id: token
        Kirigami.FormData.label: "API token:"
        Layout.fillWidth: true
        placeholderText: "<tokenId>:<tokenSecret>"
        echoMode: Controls.TextInput.Password
    }

    RowLayout {
        Kirigami.FormData.label: "KWallet integration:"
        Layout.fillWidth: true

        Controls.Button {
            text: "Save Token to KWallet"
            icon.name: "security-high"
            onClicked: configRoot.saveTokenToWallet()
        }
    }

    Kirigami.InlineMessage {
        id: statusMessage
        visible: configRoot.walletStatusMessage !== ""
        type: configRoot.walletStatusMessage.startsWith("Error") || configRoot.walletStatusMessage.startsWith("Failed")
            ? Kirigami.MessageType.Error
            : Kirigami.MessageType.Information
        text: configRoot.walletStatusMessage
        showCloseButton: true
        Layout.fillWidth: true

        // Kirigami.SelectableLabel inside allows selecting and copying the error text
        contentItem: Kirigami.SelectableLabel {
            text: statusMessage.text
            wrapMode: Text.WordWrap
        }
    }

    Controls.SpinBox {
        id: pollInterval
        Kirigami.FormData.label: "Refresh interval:"
        from: 5
        to: 3600

        textFromValue: function(value) {
            return value + " s"
        }

        valueFromText: function(text) {
            return parseInt(text)
        }
    }

    Controls.ComboBox {
        id: workingLocation
        Kirigami.FormData.label: "Default working location:"
        model: workingLocations
        currentIndex: Math.max(0, workingLocations.indexOf(cfg_workingLocation))

        onActivated: cfg_workingLocation = currentValue
    }
}

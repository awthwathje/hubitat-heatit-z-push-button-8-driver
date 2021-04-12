import groovy.transform.Field

@Field static Map COMMAND_CLASS_VERSIONS = [
        0x70: 1,    // Configuration
        0x86: 2,    // Version
        0x5B: 3,    // Central Scene
        0x80: 1,    // Battery
        0x84: 2     // Wake Up
]

metadata {
    definition (name: 'HeatIt Z-Push Button 8', namespace: 'foyl.io', author: 'Awth Wathje') {
        capability 'PushableButton'
        capability 'HoldableButton'
        capability 'ReleasableButton'
        capability 'Battery'
        capability 'Configuration'

        attribute 'batteryLevel', 'number'
        attribute 'firmwareVersion', 'string'
        attribute 'hardwareVersion', 'string'
        attribute 'slowRefresh', 'boolean'          // factory: true
        attribute 'wakeUpIntervalSeconds', 'number' // factory: 43200

        fingerprint deviceId: 'A305', inClusters: '0x5E,0x55,0x98,0x9F,0x6C', mfr: '019B', prod: '0300', deviceJoinName: 'HeatIt Z-Push Button 8'
    }

    preferences {
        input name: 'logEnable', type: 'bool', title: 'Enable logging', defaultValue: false
    }
}

void configure() {
    if (logEnable) log.info "${device.getName()}: configuring..."
    sendEvent(name: 'numberOfButtons', value: 8)
}

def parse(String description) {
    hubitat.zwave.Command cmd = zwave.parse(description, COMMAND_CLASS_VERSIONS)
    zwaveEvent(cmd)
}

// util
String secure(String cmd) {
    zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd) {
    zwaveSecureEncap(cmd)
}

void sendHubCommands(List<hubitat.zwave.Command> commands, Long delay=200) {
    sendHubCommand(new hubitat.device.HubMultiAction(
        delayBetween(commands.collect { secure(it) }, delay),
        hubitat.device.Protocol.ZWAVE
    ))
}

// commands
def getReport() {
    if (logEnable) log.info "${device.getName()}: getting reports..."
    sendHubCommands([
        zwave.versionV2.versionGet(),
        zwave.wakeUpV2.wakeUpIntervalGet(),
        zwave.batteryV1.batteryGet(),
        zwave.centralSceneV3.centralSceneConfigurationGet(),
        zwave.wakeUpV2.wakeUpNoMoreInformation()
    ], 350)
}

void sendButtonEvent(String name, Short buttonNumber, String type, String descriptionText) {
    sendEvent(name: name, value: buttonNumber, type: type, isStateChange: true, descriptionText: descriptionText)
}

void push(buttonNumber) {
    sendButtonEvent('pushed', (Short)buttonNumber, 'digital', "Button ${buttonNumber} was pushed programmatically")
}

void hold(buttonNumber) {
    sendButtonEvent('pushed', (Short)buttonNumber, 'digital', "Button ${buttonNumber} was held down programmatically")
}

void release(buttonNumber) {
    sendButtonEvent('pushed', (Short)buttonNumber, 'digital', "Button ${buttonNumber} was released programmatically")
}

// event handlers
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(COMMAND_CLASS_VERSIONS)
    if (encapsulatedCommand) zwaveEvent(encapsulatedCommand)
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    if (logEnable) log.info "${device.getName()}: wakeupv2.WakeUpNotification: ${cmd}"
    runIn(1, 'getReport')
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) {
    if (logEnable) log.info "${device.getName()}: centralscenev3.CentralSceneNotification: ${cmd}"

    Short KEY_PRESSED_1_TIME = hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_PRESSED_1_TIME
    Short KEY_HELD_DOWN = hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_HELD_DOWN
    Short KEY_RELEASED = hubitat.zwave.commands.centralscenev3.CentralSceneNotification.KEY_RELEASED

    switch (cmd.keyAttributes) {
       case KEY_PRESSED_1_TIME:
            sendButtonEvent('pushed', cmd.sceneNumber, 'physical', "Button ${cmd.sceneNumber} was pushed")
            break
       case KEY_HELD_DOWN:
            sendButtonEvent('held', cmd.sceneNumber, 'physical', "Button ${cmd.sceneNumber} was held down")
            break
       case KEY_RELEASED:
            sendButtonEvent('released', cmd.sceneNumber, 'physical', "Button ${cmd.sceneNumber} was released")
            break
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
    if (logEnable) log.info "${device.getName()}: versionv2.VersionReport: ${cmd}"
    sendEvent(name: 'firmwareVersion', value: "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
    sendEvent(name: 'hardwareVersion', value: "${cmd.hardwareVersion}")
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
    if (logEnable) log.info "${device.getName()}: wakeupv2.WakeUpIntervalReport: ${cmd}"
    sendEvent(name: 'wakeUpIntervalSeconds', value: cmd.seconds)
}

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    if (logEnable) log.info "${device.getName()}: batteryv1.BatteryReport: ${cmd}"
    sendEvent(name: 'batteryLevel', value: cmd.batteryLevel)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneConfigurationReport cmd) {
    if (logEnable) log.info "${device.getName()}: centralscenev3.CentralSceneConfigurationReport: ${cmd}"
    sendEvent(name: 'slowRefresh', value: cmd.slowRefresh)
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.info "${device.getName()}: received command was not handled hubitat.zwave.Command: ${cmd}"
}

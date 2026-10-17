package com.mosquetracker.app.model

/**
 * One thing the routine can do to the phone. The list holds everything a normal Android app is
 * actually allowed to do, so the UI can offer a real checklist instead of hand-waving. Switches
 * Android reserves for the system (Wi-Fi, Bluetooth, airplane mode and the like) are not here,
 * because no action the user can arm would ever run.
 */
enum class RoutineAction(
    /** Row title. */
    val label: String,
    /** One-line explanation of the effect. */
    val description: String,
    /** Entry actions run on arrival, exit actions run when the visit closes. */
    val phase: ActionPhase,
    /** Heading the row is filed under. */
    val group: ActionGroup,
    /** Special access Android demands before the action can do anything. */
    val requirement: ActionRequirement,
    /** Fresh-install default. */
    val defaultEnabled: Boolean,
) {

    // ---- On arrival -------------------------------------------------------

    SILENT_RINGER(
        label = "Silent ringer",
        description = "Nothing rings and nothing buzzes — the phone goes completely quiet",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.SOUND,
        requirement = ActionRequirement.DND_ACCESS,
        defaultEnabled = true,
    ),
    VIBRATE_RINGER(
        label = "Vibrate instead",
        description = "Calls and alerts buzz instead of ringing. If \"Silent ringer\" is on too, silent wins",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.SOUND,
        requirement = ActionRequirement.DND_ACCESS,
        defaultEnabled = false,
    ),
    MUTE_MEDIA(
        label = "Mute media",
        description = "Music and video volume to zero",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.SOUND,
        requirement = ActionRequirement.NONE,
        defaultEnabled = true,
    ),
    MUTE_ALARM(
        label = "Mute alarm volume",
        description = "Alarm volume to its minimum — Android never lets an alarm go fully silent",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.SOUND,
        requirement = ActionRequirement.NONE,
        defaultEnabled = true,
    ),
    MUTE_NOTIFICATION(
        label = "Mute notification volume",
        description = "Notification stream to zero",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.SOUND,
        requirement = ActionRequirement.NONE,
        defaultEnabled = true,
    ),
    MUTE_SYSTEM(
        label = "Mute system sounds",
        description = "Touch tones, keyboard clicks and the camera shutter",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.SOUND,
        requirement = ActionRequirement.NONE,
        defaultEnabled = false,
    ),

    DND_TOTAL_SILENCE(
        label = "Do Not Disturb",
        description = "Total silence — nothing gets through, not even alarms",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.FOCUS,
        requirement = ActionRequirement.DND_ACCESS,
        defaultEnabled = false,
    ),
    DND_ALARMS_ONLY(
        label = "Do Not Disturb, alarms only",
        description = "Alarms still ring, everything else stays quiet",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.FOCUS,
        requirement = ActionRequirement.DND_ACCESS,
        defaultEnabled = false,
    ),

    FLASHLIGHT_ON(
        label = "Turn on the flashlight",
        description = "Torch on for the whole visit (always switched off on exit)",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.DEVICE,
        requirement = ActionRequirement.NONE,
        defaultEnabled = false,
    ),
    VIBRATE_PULSE(
        label = "Vibrate once",
        description = "A short buzz so you feel the arrival",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.DEVICE,
        requirement = ActionRequirement.NONE,
        defaultEnabled = false,
    ),
    MIN_BRIGHTNESS(
        label = "Dim the screen",
        description = "Brightness to minimum, auto-brightness off",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.DEVICE,
        requirement = ActionRequirement.WRITE_SETTINGS,
        defaultEnabled = false,
    ),
    SHORT_SCREEN_TIMEOUT(
        label = "Shorten screen timeout",
        description = "Screen sleeps after 15 seconds",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.DEVICE,
        requirement = ActionRequirement.WRITE_SETTINGS,
        defaultEnabled = false,
    ),
    LOCK_ROTATION(
        label = "Lock the rotation",
        description = "Auto-rotate switches off while you are inside",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.DEVICE,
        requirement = ActionRequirement.WRITE_SETTINGS,
        defaultEnabled = false,
    ),

    ARRIVAL_NOTICE(
        label = "Arrival notice",
        description = "Notification naming the mosque, as soon as you enter",
        phase = ActionPhase.ON_ARRIVAL,
        group = ActionGroup.NOTICE,
        requirement = ActionRequirement.NONE,
        defaultEnabled = true,
    ),

    // ---- When the visit ends ---------------------------------------------

    RESTORE_SOUND(
        label = "Sound back to before",
        description = "Ring mode, volumes and Do Not Disturb return to their pre-arrival state",
        phase = ActionPhase.ON_EXIT,
        group = ActionGroup.SOUND,
        requirement = ActionRequirement.NONE,
        defaultEnabled = true,
    ),
    RESTORE_DEVICE(
        label = "Brightness & rotation back",
        description = "Screen brightness, timeout, auto-rotate and the flashlight are restored",
        phase = ActionPhase.ON_EXIT,
        group = ActionGroup.DEVICE,
        requirement = ActionRequirement.NONE,
        defaultEnabled = true,
    ),
    EXIT_NOTICE(
        label = "Exit notice",
        description = "Notification confirming the visit was closed",
        phase = ActionPhase.ON_EXIT,
        group = ActionGroup.NOTICE,
        requirement = ActionRequirement.NONE,
        defaultEnabled = true,
    ),
    ;

    companion object {
        /** The subset that makes up the sound profile, shared with the prayer-silence feature. */
        val SOUND_PROFILE: Set<RoutineAction> = setOf(
            SILENT_RINGER,
            VIBRATE_RINGER,
            MUTE_MEDIA,
            MUTE_ALARM,
            MUTE_NOTIFICATION,
            MUTE_SYSTEM,
            DND_TOTAL_SILENCE,
            DND_ALARMS_ONLY,
        )

        /** Which on-arrival actions fight over the ring mode. Silent wins. */
        val RINGER_ACTIONS: Set<RoutineAction> = setOf(SILENT_RINGER, VIBRATE_RINGER)

        /** [RINGER_ACTIONS] by name, for reading a stored snapshot's action list. */
        val RINGER_ACTION_NAMES: Set<String> = RINGER_ACTIONS.mapTo(mutableSetOf()) { it.name }

        val DND_ACTIONS: Set<RoutineAction> = setOf(DND_TOTAL_SILENCE, DND_ALARMS_ONLY)

        /** Heading order for the "Then" list. */
        val ON_ARRIVAL_GROUPS: List<ActionGroup> = listOf(
            ActionGroup.SOUND,
            ActionGroup.FOCUS,
            ActionGroup.DEVICE,
            ActionGroup.NOTICE,
        )

        val DEFAULT_ENABLED: Set<RoutineAction> by lazy {
            values().filter { it.defaultEnabled }.toSet()
        }

        fun onArrival(): List<RoutineAction> = values().filter { it.phase == ActionPhase.ON_ARRIVAL }

        fun onExit(): List<RoutineAction> = values().filter { it.phase == ActionPhase.ON_EXIT }
    }
}

/** Which half of the routine an action belongs to. */
enum class ActionPhase { ON_ARRIVAL, ON_EXIT }

/** Heading used to file a row inside the "Then" list. */
enum class ActionGroup(val heading: String) {
    SOUND("Sound"),
    FOCUS("Focus"),
    DEVICE("Device"),
    NOTICE("Notice"),
}

/** Special access Android demands before an action can take effect. */
enum class ActionRequirement {
    /** Works out of the box. */
    NONE,

    /** Needs Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`). */
    DND_ACCESS,

    /** Needs "Modify system settings" (`WRITE_SETTINGS`, granted from the system settings screen). */
    WRITE_SETTINGS,
}

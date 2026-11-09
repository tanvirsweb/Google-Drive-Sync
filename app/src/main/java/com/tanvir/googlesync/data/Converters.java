package com.tanvir.googlesync.data;

import androidx.room.TypeConverter;

public class Converters {
    @TypeConverter public static String fromMode(SyncMode v) { return v.name(); }
    @TypeConverter public static SyncMode toMode(String v) { return SyncMode.valueOf(v); }
    @TypeConverter public static String fromConflict(ConflictPolicy v) { return v.name(); }
    @TypeConverter public static ConflictPolicy toConflict(String v) { return ConflictPolicy.valueOf(v); }
    @TypeConverter public static String fromSchedule(ScheduleType v) { return v.name(); }
    @TypeConverter public static ScheduleType toSchedule(String v) { return ScheduleType.valueOf(v); }
    @TypeConverter public static String fromStatus(SyncStatus v) { return v.name(); }
    @TypeConverter public static SyncStatus toStatus(String v) { return SyncStatus.valueOf(v); }
}

package mchorse.bbs_mod.compat.alt;

/**
 * The part of 26.2's DeviceInfo BBS reads, for the versions before 26.1. OpenGL timestamps
 * count nanoseconds, hence a period of 1.
 */
public record DeviceInfo(String name, String vendorName, String backendName, float timestampPeriod, DeviceLimits limits)
{}

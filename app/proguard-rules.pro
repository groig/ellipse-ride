# The AIDL interface must keep its names: BRouter checks the interface descriptor.
-keep class btools.routingapp.** { *; }

# osmdroid looks some classes up by name and references optional libraries.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

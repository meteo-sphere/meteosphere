# Project-specific ProGuard/R8 rules.
# See https://developer.android.com/build/shrink-code

# The add-ons are made by the names in BuildConfig.ADD_ONS
-keep class * implements org.meteosphere.addon.AddOn {
    public <init>();
}

# The settings' screens are made by the names res/xml/settings.xml gives them (app:fragment)
-keep class org.meteosphere.ui.settings.** extends androidx.fragment.app.Fragment {
    public <init>();
}

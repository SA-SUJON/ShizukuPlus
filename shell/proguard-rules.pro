-keep class rikka.shizuku.shell.ShizukuShellLoader {
    public static void main(java.lang.String[]);
}

# dev.rikka.hidden:compat classes extend hidden framework stubs (IProcessObserver$Stub,
# IUidObserver$Stub, IPackageManager.Stub, etc.). R8 + -repackageclasses rewrites their class
# hierarchy in a way ART 16 rejects at class-definition time (VerifyError). Keeping the whole
# package (not just adapter.**) ensures we catch any hidden-stub subclass in the library,
# regardless of which subpackage it lives in.
-keep class rikka.hidden.compat.** { *; }

-allowaccessmodification
-repackageclasses

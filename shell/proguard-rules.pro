-keep class rikka.shizuku.shell.ShizukuShellLoader {
    public static void main(java.lang.String[]);
}

# rikka.hidden.compat.adapter classes extend hidden framework stubs (IProcessObserver$Stub,
# IUidObserver$Stub, etc.). R8 + -repackageclasses rewrites their class hierarchy in a way
# ART 16 rejects at class-definition time (VerifyError). Keeping them prevents that rewrite.
-keep class rikka.hidden.compat.adapter.** { *; }

-allowaccessmodification
-repackageclasses

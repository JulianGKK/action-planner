#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>

/* Public Android libc API, available at this project's minSdk 30.
 * Kernel-enforced atomic no-replace. No copy/rename fallback, logs or key material.
 * The caller validates the private namespace and holds DocumentNamespaceLane.
 */
JNIEXPORT jint JNICALL
Java_org_pag_data_product_DocumentRenameNative_move(JNIEnv *env, jobject self,
                                                    jstring source, jstring target) {
    (void)self;
    if (source == NULL || target == NULL) return EINVAL;
    const char *from = (*env)->GetStringUTFChars(env, source, NULL);
    if (from == NULL) return ENOMEM;
    const char *to = (*env)->GetStringUTFChars(env, target, NULL);
    if (to == NULL) {
        (*env)->ReleaseStringUTFChars(env, source, from);
        return ENOMEM;
    }
    int result = renameat2(AT_FDCWD, from, AT_FDCWD, to, RENAME_NOREPLACE);
    int code = result == 0 ? 0 : errno;
    (*env)->ReleaseStringUTFChars(env, target, to);
    (*env)->ReleaseStringUTFChars(env, source, from);
    return code;
}

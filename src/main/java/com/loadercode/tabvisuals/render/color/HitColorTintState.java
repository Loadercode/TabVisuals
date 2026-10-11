/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.color;

public final class HitColorTintState {
    private HitColorTintState() {}
    public static final ThreadLocal<Boolean> SHOULD_TINT = ThreadLocal.withInitial(() -> false);
}

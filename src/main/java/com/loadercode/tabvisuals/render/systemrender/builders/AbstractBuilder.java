/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.systemrender.builders;

public abstract class AbstractBuilder<T> {

	/** Защита от вложенного build(): сброс общего стейта — только внешнему вызову. */
	private boolean building;

	public AbstractBuilder() {
		this.reset();
	}

	public final T build() {
		if (this.building) {
			// Вложенный build (из колбэка): собираем из текущих полей,
			// но стейт не сбрасываем — иначе внешний build() продолжит на дефолтах.
			return this._build();
		}
		this.building = true;
		try {
			T instance = this._build();
			this.reset();
			return instance;
		} finally {
			this.building = false;
		}
	}

	protected abstract void reset();

	protected abstract T _build();

}
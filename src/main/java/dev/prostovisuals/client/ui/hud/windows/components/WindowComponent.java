package dev.prostovisuals.client.ui.hud.windows.components;

import dev.prostovisuals.client.ui.hud.windows.components.impl.BooleanComponent;
import dev.prostovisuals.client.ui.clickgui.components.Component;
import dev.prostovisuals.client.util.animations.Animation;
import lombok.*;

@Getter @Setter
public abstract class WindowComponent extends Component {
	protected Animation animation;

	public WindowComponent(String name) {
		super(name);
	}
}
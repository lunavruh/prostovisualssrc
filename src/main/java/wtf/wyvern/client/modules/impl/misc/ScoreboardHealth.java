package wtf.wyvern.client.modules.impl.misc;

import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;

@ModuleAnnotation(
   name = "ScoreboardHealth",
   category = Category.MISC,
   description = "Фиксит хп цели если оно фейк"
)
public class ScoreboardHealth extends Module {
   public static final ScoreboardHealth INSTANCE = new ScoreboardHealth();
}
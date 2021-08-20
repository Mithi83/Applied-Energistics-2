package appeng.datagen.providers.recipes;

import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.minecraft.core.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.ItemStack;

public abstract class AE2RecipeProvider extends RecipeProvider {
    public AE2RecipeProvider(DataGenerator generator) {
        super(generator);
    }

    public static JsonObject toJson(ItemStack stack) {
        var stackObj = new JsonObject();
        stackObj.addProperty("item", Registry.ITEM.getKey(stack.getItem()).toString());
        if (stack.getCount() > 1) {
            stackObj.addProperty("count", stack.getCount());
        }
        return stackObj;
    }

    @Override
    protected final void buildCraftingRecipes(Consumer<FinishedRecipe> consumer) {
        buildAE2CraftingRecipes(consumer);
    }

    protected abstract void buildAE2CraftingRecipes(Consumer<FinishedRecipe> consumer);

}

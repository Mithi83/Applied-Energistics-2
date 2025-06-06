/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.me.common;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import appeng.core.AEConfig;

/**
 * @author AlgorithmX2
 * @author thatsIch
 * @version rv2
 * @since rv0
 */
public class StackSizeRenderer {
    private static void renderSizeLabel(Matrix4f matrix, Font fontRenderer, float xPos, float yPos, String text,
            boolean largeFonts) {
        final float scaleFactor = largeFonts ? 0.85f : 0.666f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = largeFonts ? 0 : -1;

        RenderSystem.disableBlend();
        final int X = (int) ((xPos + offset + 16.0f + 2.0f - fontRenderer.width(text) * scaleFactor)
                * inverseScaleFactor);
        final int Y = (int) ((yPos + offset + 16.0f - 5.0f * scaleFactor) * inverseScaleFactor);
        var buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        fontRenderer.drawInBatch(text, X + 1, Y + 1, 0x413f54, false, matrix, buffer, Font.DisplayMode.NORMAL, 0,
                15728880);
        fontRenderer.drawInBatch(text, X, Y, 0xffffff, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        buffer.endBatch();
        RenderSystem.enableBlend();
    }

    public static void renderSizeLabel(GuiGraphics guiGraphics, Font fontRenderer, float xPos, float yPos,
            String text) {
        renderSizeLabel(guiGraphics, fontRenderer, xPos, yPos, text, AEConfig.instance().isUseLargeFonts());
    }

    public static void renderSizeLabel(GuiGraphics guiGraphics, Font fontRenderer, float xPos, float yPos, String text,
            boolean largeFonts) {
        final float scaleFactor = largeFonts ? 0.85f : 0.666f;

        var stack = guiGraphics.pose();
        stack.pushPose();
        // According to ItemRenderer, text is 200 above items.
        stack.translate(0, 0, 200);
        stack.scale(scaleFactor, scaleFactor, scaleFactor);

        renderSizeLabel(stack.last().pose(), fontRenderer, xPos, yPos, text, largeFonts);

        stack.popPose();
    }

}

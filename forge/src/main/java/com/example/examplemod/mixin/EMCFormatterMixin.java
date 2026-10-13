package com.example.examplemod.mixin;

import com.example.examplemod.util.EMCFormatUtil;
import moze_intel.projecte.utils.Constants;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.text.NumberFormat;

@Mixin(value = Constants.class, remap = false)
public class EMCFormatterMixin {
    @Final
    @Shadow
    private static final NumberFormat EMC_FORMATTER = EMCFormatUtil.INSTANCE;
}

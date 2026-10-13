package com.example.examplemod.util;

import com.example.examplemod.Config;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import net.minecraft.client.gui.screens.Screen;

public class EMCFormatUtil extends DecimalFormat {
    public static final EMCFormatUtil INSTANCE = new EMCFormatUtil();
    private static final DecimalFormat COMMA_FORMAT = new DecimalFormat("#,###");

    private EMCFormatUtil() {
        super("#,###");
    }

    @Override
    public StringBuffer format(double number, StringBuffer result, FieldPosition fieldPosition) {
        return new StringBuffer(format(BigDecimal.valueOf(number).toBigInteger()));
    }

    @Override
    public StringBuffer format(long number, StringBuffer result, FieldPosition fieldPosition) {
        return new StringBuffer(format(BigInteger.valueOf(number)));
    }

    public static String format(BigInteger value) {
        boolean configReady = Config.CLIENT.spec.isLoaded();
        if (Screen.hasShiftDown() || (configReady && !Config.CLIENT.formatEMC.get())) {
            return COMMA_FORMAT.format(value);
        }
        return abbreviate(value, !configReady || Config.CLIENT.emcShortNames.get());
    }

    private static String abbreviate(BigInteger value, boolean shortNames) {
        if (value.compareTo(BigInteger.valueOf(1_000_000L)) < 0) {
            return COMMA_FORMAT.format(value);
        }
        String str = value.toString();
        int first = str.length() % 3;
        if (first == 0) first = 3;
        String sig = str.substring(0, first);
        String dec = str.substring(first, Math.min(first + 2, str.length()));
        int idx = (str.length() - 1) / 3 - 2;
        return sig + "." + dec + " " + (shortNames ? SHORT[idx] : LONG[idx]);
    }

    private static final String[] SHORT = {"M", "B", "T", "Qa", "Qi", "Sx", "Sp", "O", "N", "D", "U", "Du", "Tr", "Qt", "Qd", "Sd", "St", "Oc", "No"};
    private static final String[] LONG = {"Million", "Billion", "Trillion", "Quadrillion", "Quintillion", "Sextillion", "Septillion", "Octillion", "Nonillion", "Decillion", "Undecillion", "Duodecillion", "Tredecillion", "Quattuordecillion", "Quindecillion", "Sexdecillion", "Septendecillion", "Octodecillion", "Novemdecillion"};
}

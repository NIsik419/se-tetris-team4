// package component;

// import org.junit.Test;
// import java.awt.Color;

// import static org.junit.Assert.*;

// public class ColorBlindPaletteTest {

//     @Test
//     public void testConvertNormalReturnsSameColor() {
//         Color original = Color.RED;
//         Color converted = ColorBlindPalette.convert(original, ColorBlindPalette.Mode.NORMAL);
//         assertEquals(original.getRGB(), converted.getRGB());
//     }

//     @Test
//     public void testConvertAllModes() {
//         Color base = new Color(100, 150, 200);
//         for (ColorBlindPalette.Mode mode : ColorBlindPalette.Mode.values()) {
//             Color converted = ColorBlindPalette.convert(base, mode);
//             assertNotNull(converted);
//         }
//     }

//     @Test
//     public void testGetPaletteReturnsSameLength() {
//         Color[] arr = ColorBlindPalette.getPalette(ColorBlindPalette.Mode.DEUTER);
//         assertEquals(ColorBlindPalette.BASE_COLORS.length, arr.length);
//         assertNotNull(arr[0]);
//     }

//     @Test
//     public void testBrightnessIncreasesInMode() {
//         Color brightened = ColorBlindPalette.convert(Color.GREEN, ColorBlindPalette.Mode.DEUTER);
//         assertTrue(brightened.getRGB() != Color.GREEN.getRGB());
//     }
// }

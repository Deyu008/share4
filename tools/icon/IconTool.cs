using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class IconTool
{
    const int WORK = 1024;   // working resolution for the alpha pass

    /// <summary>
    /// Extract the glyph as a transparent foreground.
    ///  1) read the source at WORK resolution, alpha = ramp(luminance)
    ///  2) find the glyph bbox, draw that region into the canvas scaled to markFrac
    /// </summary>
    public static void ExtractMark(string src, string dst, int canvas, double markFrac,
                                   int lo, int hi, bool white)
    {
        // --- 1. downscale source to WORK (explicit source rect => pixel correct) ---
        Bitmap s = new Bitmap(src);
        Bitmap work = new Bitmap(WORK, WORK, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(work))
        {
            g.Clear(Color.Black);
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.DrawImage(s, new Rectangle(0, 0, WORK, WORK), 0, 0, s.Width, s.Height, GraphicsUnit.Pixel);
        }
        s.Dispose();

        // --- 2. alpha pass ---
        Bitmap alpha = new Bitmap(WORK, WORK, PixelFormat.Format32bppArgb);
        var r = new Rectangle(0, 0, WORK, WORK);
        var d = alpha.LockBits(r, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        int stride = d.Stride;
        byte[] buf = new byte[stride * WORK];
        int bx0 = WORK, by0 = WORK, bx1 = -1, by1 = -1;
        int bboxThresh = lo + (hi - lo) / 2;
        for (int y = 0; y < WORK; y++)
        {
            for (int x = 0; x < WORK; x++)
            {
                Color c = work.GetPixel(x, y);
                int lum = (int)(0.299 * c.R + 0.587 * c.G + 0.114 * c.B);
                double t = (lum - lo) / (double)(hi - lo);
                if (t < 0) t = 0;
                if (t > 1) t = 1;
                int i = y * stride + x * 4;
                buf[i + 0] = (byte)(white ? 255 : c.B);
                buf[i + 1] = (byte)(white ? 255 : c.G);
                buf[i + 2] = (byte)(white ? 255 : c.R);
                buf[i + 3] = (byte)Math.Round(t * 255);
                if (lum > bboxThresh)
                {
                    if (x < bx0) bx0 = x;
                    if (x > bx1) bx1 = x;
                    if (y < by0) by0 = y;
                    if (y > by1) by1 = y;
                }
            }
        }
        Marshal.Copy(buf, 0, d.Scan0, buf.Length);
        alpha.UnlockBits(d);
        work.Dispose();

        if (bx1 < 0) { bx0 = 0; by0 = 0; bx1 = WORK - 1; by1 = WORK - 1; }
        Rectangle bb = new Rectangle(bx0, by0, bx1 - bx0 + 1, by1 - by0 + 1);
        Console.WriteLine("  glyph bbox " + bb.X + "," + bb.Y + " " + bb.Width + "x" + bb.Height);

        // --- 3. draw the glyph region into the canvas, scaled to markFrac ---
        double scale = (canvas * markFrac) / Math.Max(bb.Width, bb.Height);
        int tw = Math.Max(1, (int)Math.Round(bb.Width * scale));
        int th = Math.Max(1, (int)Math.Round(bb.Height * scale));

        Bitmap o = new Bitmap(canvas, canvas, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(o))
        {
            g.Clear(Color.Transparent);
            g.CompositingMode = CompositingMode.SourceCopy;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.DrawImage(alpha,
                new Rectangle((canvas - tw) / 2, (canvas - th) / 2, tw, th),
                bb.X, bb.Y, bb.Width, bb.Height, GraphicsUnit.Pixel);
        }
        alpha.Dispose();
        o.Save(dst, ImageFormat.Png);
        o.Dispose();
    }

    /// <summary>Downscale an image to a square PNG (no alpha change)</summary>
    public static void Resize(string src, string dst, int size)
    {
        Bitmap b = new Bitmap(src);
        Bitmap o = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(o))
        {
            g.Clear(Color.Transparent);
            g.CompositingMode = CompositingMode.SourceCopy;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.DrawImage(b, new Rectangle(0, 0, size, size), 0, 0, b.Width, b.Height, GraphicsUnit.Pixel);
        }
        b.Dispose();
        o.Save(dst, ImageFormat.Png);
        o.Dispose();
    }

    /// <summary>Background layer: weibo blue gradient plus soft radial highlights</summary>
    public static void MakeBackground(string dst, int canvas)
    {
        Bitmap o = new Bitmap(canvas, canvas, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(o))
        {
            g.Clear(Color.FromArgb(255, 18, 50, 98));
            // 底色渐变(不透明,正常混合即可)
            using (var br = new LinearGradientBrush(
                new Point(0, 0), new Point(canvas, canvas),
                Color.FromArgb(255, 74, 142, 220),
                Color.FromArgb(255, 18, 50, 98)))
            {
                g.FillRectangle(br, 0, 0, canvas, canvas);
            }
            // 左上柔光:用 PathGradientBrush 画椭圆再正常混合(半透明外围叠在底色上)
            float r = canvas * 0.75f;
            using (var path = new GraphicsPath())
            {
                path.AddEllipse(canvas * 0.08f - r, canvas * 0.04f - r, r * 2, r * 2);
                using (var br = new PathGradientBrush(path))
                {
                    br.CenterColor = Color.FromArgb(120, 255, 255, 255);
                    br.SurroundColors = new Color[] { Color.FromArgb(0, 255, 255, 255) };
                    g.FillEllipse(br, canvas * 0.08f - r, canvas * 0.04f - r, r * 2, r * 2);
                }
            }
            // 右下暖光
            float r2 = canvas * 0.55f;
            using (var path = new GraphicsPath())
            {
                path.AddEllipse(canvas * 0.94f - r2, canvas * 0.92f - r2, r2 * 2, r2 * 2);
                using (var br = new PathGradientBrush(path))
                {
                    br.CenterColor = Color.FromArgb(70, 232, 130, 30);
                    br.SurroundColors = new Color[] { Color.FromArgb(0, 232, 130, 30) };
                    g.FillEllipse(br, canvas * 0.94f - r2, canvas * 0.92f - r2, r2 * 2, r2 * 2);
                }
            }
        }
        o.Save(dst, ImageFormat.Png);
        o.Dispose();
    }

    /// <summary>Compose the legacy (non-adaptive) icon: rounded gradient plate + glyph</summary>
    public static void ComposeLegacy(string bg, string fg, string dst, int size, double cornerFrac, double markScale, bool round)
    {
        Bitmap b = new Bitmap(bg);
        Bitmap f = new Bitmap(fg);
        Bitmap o = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(o))
        {
            g.Clear(Color.Transparent);
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;

            using (var path = new GraphicsPath())
            {
                if (round)
                {
                    path.AddEllipse(0, 0, size, size);
                }
                else
                {
                    float rad = (float)(size * cornerFrac);
                    path.AddArc(0, 0, rad * 2, rad * 2, 180, 90);
                    path.AddArc(size - rad * 2, 0, rad * 2, rad * 2, 270, 90);
                    path.AddArc(size - rad * 2, size - rad * 2, rad * 2, rad * 2, 0, 90);
                    path.AddArc(0, size - rad * 2, rad * 2, rad * 2, 90, 90);
                    path.CloseFigure();
                }
                GraphicsState st = g.Save();
                g.SetClip(path);
                g.CompositingMode = CompositingMode.SourceCopy;
                g.DrawImage(b, new Rectangle(0, 0, size, size));
                g.CompositingMode = CompositingMode.SourceOver;
                g.Restore(st);
            }

            int tw = (int)Math.Round(size * markScale);
            g.DrawImage(f, new Rectangle((size - tw) / 2, (size - tw) / 2, tw, tw));
        }
        b.Dispose(); f.Dispose();
        o.Save(dst, ImageFormat.Png);
        o.Dispose();
    }
}

using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

// Renders the launcher icon assets for the faceted "S" mark.
//
// The mark itself comes from tools/icon/s-mark-source.jpg (generated artwork).
// This tool keys the black background out, scales the mark into the adaptive
// icon safe zone, and produces the per-density foreground / monochrome layers
// plus the 512x512 store listing image.
//
// The background plate is a VECTOR (app/src/main/res/drawable/ic_launcher_bg.xml);
// the plate rendered here must match it.
//
// NOTE: only basic GDI+ APIs are used on purpose - some PowerShell sandboxes
// expose a reduced System.Drawing (no LinearGradientBrush multi-stop
// constructors, no ColorBlend, no LinearGradientMode.Linear).
public static class IconMark
{
    /// <summary>Key the mark out of its black background and scale it into the canvas.</summary>
    /// <param name="mode">0 = alpha from luminance, 1 = alpha from max channel (keeps dark navy facets)</param>
    public static void ExtractMark(string src, string dst, int canvas, double markFrac,
                                   int lo, int hi, bool white, int mode)
    {
        Bitmap s = new Bitmap(src);
        int W = 1024, H = 1024;
        Bitmap work = new Bitmap(W, H, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(work))
        {
            g.Clear(Color.Black);
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.DrawImage(s, new Rectangle(0, 0, W, H), 0, 0, s.Width, s.Height, GraphicsUnit.Pixel);
        }
        s.Dispose();

        Bitmap alpha = new Bitmap(W, H, PixelFormat.Format32bppArgb);
        var rect = new Rectangle(0, 0, W, H);
        var d = alpha.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        int stride = d.Stride;
        byte[] buf = new byte[stride * H];
        int bx0 = W, by0 = H, bx1 = -1, by1 = -1;
        int bboxThresh = lo + (hi - lo) / 2;
        for (int y = 0; y < H; y++)
        {
            for (int x = 0; x < W; x++)
            {
                Color c = work.GetPixel(x, y);
                int key = (mode == 1)
                    ? Math.Max(c.R, Math.Max(c.G, c.B))
                    : (int)(0.299 * c.R + 0.587 * c.G + 0.114 * c.B);
                double t = (key - lo) / (double)(hi - lo);
                if (t < 0) t = 0;
                if (t > 1) t = 1;
                int i = y * stride + x * 4;
                buf[i + 0] = (byte)(white ? 255 : c.B);
                buf[i + 1] = (byte)(white ? 255 : c.G);
                buf[i + 2] = (byte)(white ? 255 : c.R);
                buf[i + 3] = (byte)Math.Round(t * 255);
                if (key > bboxThresh)
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

        if (bx1 < 0) { bx0 = 0; by0 = 0; bx1 = W - 1; by1 = H - 1; }
        Rectangle bb = new Rectangle(bx0, by0, bx1 - bx0 + 1, by1 - by0 + 1);
        Console.WriteLine("  mark bbox " + bb.X + "," + bb.Y + " " + bb.Width + "x" + bb.Height);

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

    static Color Lerp(Color a, Color b, double t)
    {
        if (t < 0) t = 0; if (t > 1) t = 1;
        return Color.FromArgb(255,
            (int)Math.Round(a.R + (b.R - a.R) * t),
            (int)Math.Round(a.G + (b.G - a.G) * t),
            (int)Math.Round(a.B + (b.B - a.B) * t));
    }

    /// <summary>
    /// The light plate, per pixel, matching drawable/ic_launcher_bg.xml:
    /// diagonal gradient #FFFFFF -> #E4EEF9 -> #B9D4EC plus a teal glow bottom right.
    /// </summary>
    public static void MakePlate(string dst, int canvas)
    {
        Bitmap o = new Bitmap(canvas, canvas, PixelFormat.Format32bppArgb);
        var rect = new Rectangle(0, 0, canvas, canvas);
        var data = o.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        int stride = data.Stride;
        byte[] buf = new byte[stride * canvas];
        double k = canvas / 108.0;
        Color p0 = Color.FromArgb(255, 0xFF, 0xFF, 0xFF);
        Color p1 = Color.FromArgb(255, 0xE6, 0xF0, 0xFA);
        Color p2 = Color.FromArgb(255, 0x6F, 0xA6, 0xD2);
        double diag = (canvas > 1) ? (canvas - 1.0) * 2 : 1.0;
        for (int y = 0; y < canvas; y++)
        {
            for (int x = 0; x < canvas; x++)
            {
                double t = (x + y) / diag;
                Color c = t < 0.5 ? Lerp(p0, p1, t * 2) : Lerp(p1, p2, (t - 0.5) * 2);
                // bottom-right teal glow
                double dx = x - 94 * k, dy = y - 97 * k;
                double d = Math.Sqrt(dx * dx + dy * dy) / (56 * k);
                if (d < 1)
                {
                    double a = 0.30 * (1 - d) * (1 - d);
                    c = Lerp(c, Color.FromArgb(255, 0x5F, 0xD0, 0xDC), a);
                }
                int i = y * stride + x * 4;
                buf[i + 0] = c.B; buf[i + 1] = c.G; buf[i + 2] = c.R; buf[i + 3] = 255;
            }
        }
        Marshal.Copy(buf, 0, data.Scan0, buf.Length);
        o.UnlockBits(data);
        o.Save(dst, ImageFormat.Png);
        o.Dispose();
    }

    /// <summary>Store listing image: circular plate + the mark.</summary>
    public static void ComposeStore(string plate, string mark, string dst, int size)
    {
        Bitmap p = new Bitmap(plate);
        Bitmap m = new Bitmap(mark);
        Bitmap o = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(o))
        {
            g.Clear(Color.Transparent);
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.DrawImage(p, new Rectangle(0, 0, size, size));
            g.DrawImage(m, 0, 0, size, size);
        }
        p.Dispose(); m.Dispose();

        // circular mask
        Bitmap mask = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics mg = Graphics.FromImage(mask))
        {
            mg.SmoothingMode = SmoothingMode.AntiAlias;
            mg.Clear(Color.Transparent);
            mg.FillEllipse(Brushes.White, 0, 0, size, size);
        }
        int mstride;
        var md = mask.LockBits(new Rectangle(0, 0, size, size), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        mstride = md.Stride;
        byte[] mbuf = new byte[mstride * size];
        Marshal.Copy(md.Scan0, mbuf, 0, mbuf.Length);
        mask.UnlockBits(md);

        int stride;
        var od = o.LockBits(new Rectangle(0, 0, size, size), ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        stride = od.Stride;
        byte[] obuf = new byte[stride * size];
        Marshal.Copy(od.Scan0, obuf, 0, obuf.Length);
        for (int y = 0; y < size; y++)
        {
            int ro = y * stride, rm = y * mstride;
            for (int x = 0; x < size; x++)
            {
                int a = obuf[ro + x * 4 + 3];
                int mk = mbuf[rm + x * 4 + 3];
                obuf[ro + x * 4 + 3] = (byte)(a * mk / 255);
            }
        }
        Marshal.Copy(obuf, 0, od.Scan0, obuf.Length);
        o.UnlockBits(od);
        mask.Dispose();
        o.Save(dst, ImageFormat.Png);
        o.Dispose();
    }
}

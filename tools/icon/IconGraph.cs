using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

// Renders the launcher icon as a raster (store listing 512x512).
// The app's own adaptive icon is the VECTOR in res/drawable/ic_launcher_*.xml,
// which is the source of truth; the geometry below mirrors it 1:1 (108-unit space).
//
// NOTE: deliberately uses only basic GDI+ calls (Bitmap/Graphics/SolidBrush/Pen),
// because Add-Type in some PowerShell sandboxes exposes a reduced System.Drawing.
public static class IconGraph
{
    // ---- palette (matches the vector drawables) ----
    static readonly Color Bg0 = Color.FromArgb(255, 0x3E, 0x86, 0xD6);
    static readonly Color Bg1 = Color.FromArgb(255, 0x22, 0x55, 0x9F);
    static readonly Color Bg2 = Color.FromArgb(255, 0x0D, 0x2A, 0x54);
    static readonly Color NodeLight = Color.FromArgb(255, 0xFF, 0xFF, 0xFF);
    static readonly Color NodeMid = Color.FromArgb(255, 0xE2, 0xF0, 0xFE);
    static readonly Color NodeEdge = Color.FromArgb(255, 0xB4, 0xD4, 0xF2);
    static readonly Color HubMid = Color.FromArgb(255, 0xDC, 0xED, 0xFD);
    static readonly Color HubEdge = Color.FromArgb(255, 0xA8, 0xCB, 0xEE);
    static readonly Color NodeOrangeLight = Color.FromArgb(255, 0xFF, 0xC0, 0x78);
    static readonly Color NodeOrangeMid = Color.FromArgb(255, 0xF3, 0x9B, 0x3C);
    static readonly Color NodeOrangeEdge = Color.FromArgb(255, 0xE0, 0x71, 0x1A);
    static readonly Color EdgeColor = Color.FromArgb(0xCC, 255, 255, 255);

    static readonly float[] EdgeX = { 48.5f, 59.5f, 34.9f, 73.1f };
    static readonly float[] EdgeY = { 36.5f, 36.5f, 62.3f, 62.3f };
    static readonly float[] EdgeX2 = { 34.2f, 73.8f, 49.1f, 58.9f };
    static readonly float[] EdgeY2 = { 53.1f, 53.1f, 74.7f, 74.7f };

    static Color Lerp(Color a, Color b, double t)
    {
        if (t < 0) t = 0; if (t > 1) t = 1;
        return Color.FromArgb(
            (int)Math.Round(a.R + (b.R - a.R) * t),
            (int)Math.Round(a.G + (b.G - a.G) * t),
            (int)Math.Round(a.B + (b.B - a.B) * t));
    }

    static Color Lerp3(Color a, Color b, Color c, double t)
    {
        if (t < 0.5) return Lerp(a, b, t * 2);
        return Lerp(b, c, (t - 0.5) * 2);
    }

    /// <summary>Glossy bead: base disc + offset specular highlight, gives a pearl / sphere read</summary>
    static void Bead(Graphics g, float cx, float cy, float r,
                     Color light, Color mid, Color edge)
    {
        using (var b = new SolidBrush(edge))
            g.FillEllipse(b, cx - r, cy - r, r * 2, r * 2);
        using (var b = new SolidBrush(mid))
            g.FillEllipse(b, cx - r * 0.93f, cy - r * 0.93f, r * 1.86f, r * 1.86f);
        // 高光:半径 0.5r 的圆,中心向左上偏移
        float hr = r * 0.5f;
        using (var b = new SolidBrush(light))
            g.FillEllipse(b, cx - r * 0.2f - hr, cy - r * 0.22f - hr, hr * 2, hr * 2);
    }

    public static void Render(string dst, int size, double cornerFrac)
    {
        double k = size / 108.0;

        // ---------- background: per pixel (diagonal gradient + 2 soft glows) ----------
        Bitmap o = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        var rect = new Rectangle(0, 0, size, size);
        var data = o.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        int stride = data.Stride;
        byte[] buf = new byte[stride * size];
        double diag = (size > 1) ? (size - 1.0) * 2 : 1.0;
        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                double t = (x + y) / diag;
                Color c = Lerp3(Bg0, Bg1, Bg2, t);

                // top-left soft white light
                double d1 = Math.Sqrt((x - 18 * k) * (x - 18 * k) + (y - 12 * k) * (y - 12 * k)) / (62 * k);
                if (d1 < 1) c = Lerp(c, Color.White, 0.30 * (1 - d1) * (1 - d1));

                // bottom-right warm glow
                double d2 = Math.Sqrt((x - 86 * k) * (x - 86 * k) + (y - 94 * k) * (y - 94 * k)) / (52 * k);
                if (d2 < 1) c = Lerp(c, Color.FromArgb(0xE8, 0x82, 0x1E), 0.30 * (1 - d2) * (1 - d2));

                // orange bloom around the accent node
                double d3 = Math.Sqrt((x - 78 * k) * (x - 78 * k) + (y - 58 * k) * (y - 58 * k)) / (18 * k);
                if (d3 < 1) c = Lerp(c, Color.FromArgb(0xFF, 0xA4, 0x45), 0.55 * (1 - d3) * (1 - d3));

                int i = y * stride + x * 4;
                buf[i + 0] = c.B; buf[i + 1] = c.G; buf[i + 2] = c.R; buf[i + 3] = 255;
            }
        }
        Marshal.Copy(buf, 0, data.Scan0, buf.Length);
        o.UnlockBits(data);

        // ---------- foreground: edges + nodes ----------
        using (Graphics g = Graphics.FromImage(o))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;

            using (var pen = new Pen(EdgeColor, (float)(4 * k)))
            {
                pen.StartCap = LineCap.Round;
                pen.EndCap = LineCap.Round;
                pen.LineJoin = LineJoin.Round;
                for (int i = 0; i < 4; i++)
                {
                    g.DrawLine(pen, EdgeX[i] * (float)k, EdgeY[i] * (float)k,
                                   EdgeX2[i] * (float)k, EdgeY2[i] * (float)k);
                }
            }

            Bead(g, (float)(54 * k), (float)(30 * k), (float)(8.5 * k),
                 NodeLight, HubMid, HubEdge);
            Bead(g, (float)(30 * k), (float)(58 * k), (float)(6.5 * k),
                 NodeLight, NodeMid, NodeEdge);
            Bead(g, (float)(78 * k), (float)(58 * k), (float)(6.5 * k),
                 NodeOrangeLight, NodeOrangeMid, NodeOrangeEdge);
            Bead(g, (float)(54 * k), (float)(79 * k), (float)(6.5 * k),
                 NodeLight, NodeMid, NodeEdge);
        }

        // ---------- rounded-corner mask ----------
        if (cornerFrac > 0)
        {
            Bitmap mask = new Bitmap(size, size, PixelFormat.Format32bppArgb);
            using (Graphics mg = Graphics.FromImage(mask))
            {
                mg.SmoothingMode = SmoothingMode.AntiAlias;
                mg.Clear(Color.Transparent);
                float rad = (float)(size * cornerFrac);
                using (var path = new GraphicsPath())
                {
                    path.AddArc(0, 0, rad * 2, rad * 2, 180, 90);
                    path.AddArc(size - rad * 2, 0, rad * 2, rad * 2, 270, 90);
                    path.AddArc(size - rad * 2, size - rad * 2, rad * 2, rad * 2, 0, 90);
                    path.AddArc(0, size - rad * 2, rad * 2, rad * 2, 90, 90);
                    path.CloseFigure();
                    mg.FillPath(Brushes.White, path);
                }
            }
            int mstride;
            var md = mask.LockBits(new Rectangle(0, 0, size, size), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            mstride = md.Stride;
            byte[] mbuf = new byte[mstride * size];
            Marshal.Copy(md.Scan0, mbuf, 0, mbuf.Length);
            mask.UnlockBits(md);

            var od = o.LockBits(rect, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
            byte[] obuf = new byte[stride * size];
            Marshal.Copy(od.Scan0, obuf, 0, obuf.Length);
            for (int y = 0; y < size; y++)
            {
                int ro = y * stride, rm = y * mstride;
                for (int x = 0; x < size; x++)
                {
                    int a = obuf[ro + x * 4 + 3];
                    int m = mbuf[rm + x * 4 + 3];
                    obuf[ro + x * 4 + 3] = (byte)(a * m / 255);
                }
            }
            Marshal.Copy(obuf, 0, od.Scan0, obuf.Length);
            o.UnlockBits(od);
            mask.Dispose();
        }

        o.Save(dst, ImageFormat.Png);
        o.Dispose();
    }
}

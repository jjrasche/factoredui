from PIL import Image, ImageDraw, ImageFont

FONT_PATH = "C:/Windows/Fonts/consolab.ttf"


def text_mask(text, size):
    face = ImageFont.truetype(FONT_PATH, size)
    probe = Image.new("L", (1, 1))
    left, top, right, bottom = ImageDraw.Draw(probe).textbbox((0, 0), text, font=face)
    mask = Image.new("L", (right - left + 2, bottom - top + 2), 0)
    ImageDraw.Draw(mask).text((1 - left, 1 - top), text, font=face, fill=255)
    return mask.point(lambda value: 255 if value > 112 else 0)


def pixel_text(canvas, x, y, text, color, size=9):
    mask = text_mask(text, size)
    canvas.image.paste(Image.new("RGBA", mask.size, color), (round(x), round(y)), mask)
    return mask.size

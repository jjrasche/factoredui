from PIL import Image, ImageDraw


def hashed(x, y, seed=0):
    value = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    value = ((value ^ (value >> 13)) * 1274126177) & 0xFFFFFFFF
    return (value ^ (value >> 16)) & 0xFFFF


class Canvas:
    def __init__(self, width, height, background=(0, 0, 0, 0)):
        self.image = Image.new("RGBA", (width, height), background)
        self.draw = ImageDraw.Draw(self.image)

    @property
    def size(self):
        return self.image.size

    @property
    def width(self):
        return self.image.width

    @property
    def height(self):
        return self.image.height

    def polygon(self, points, fill):
        self.draw.polygon([(round(x), round(y)) for x, y in points], fill=fill)

    def ellipse(self, centre, radius_x, radius_y, fill):
        cx, cy = centre
        self.draw.ellipse((round(cx - radius_x), round(cy - radius_y), round(cx + radius_x) - 1, round(cy + radius_y) - 1), fill=fill)

    def line(self, start, end, fill, width=1):
        self.draw.line([(round(start[0]), round(start[1])), (round(end[0]), round(end[1]))], fill=fill, width=width)

    def rect(self, left, top, right, bottom, fill):
        left, top = round(left), round(top)
        right, bottom = max(round(right), left + 1), max(round(bottom), top + 1)
        self.draw.rectangle((left, top, right - 1, bottom - 1), fill=fill)

    def put(self, x, y, color):
        x, y = round(x), round(y)
        if 0 <= x < self.image.width and 0 <= y < self.image.height:
            self.image.putpixel((x, y), color)

    def get(self, x, y):
        return self.image.getpixel((round(x), round(y)))

    def paste(self, other, left, top):
        self.image.alpha_composite(other.image if isinstance(other, Canvas) else other, (round(left), round(top)))


def scaled(image, factor):
    return image.resize((image.width * factor, image.height * factor), Image.NEAREST)

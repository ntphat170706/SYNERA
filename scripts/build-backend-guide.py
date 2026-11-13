"""Create the Vietnamese PDF supplement from its maintained Markdown source."""
from pathlib import Path
import re
from html import escape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether

root = Path(__file__).resolve().parents[1]
out = root / 'output/pdf/SYNERA-Bo-sung-huong-dan-build-Backend.pdf'
out.parent.mkdir(parents=True, exist_ok=True)
fontdir = Path('C:/Windows/Fonts')
pdfmetrics.registerFont(TTFont('ArialVN', str(fontdir / 'arial.ttf')))
pdfmetrics.registerFont(TTFont('ArialVN-Bold', str(fontdir / 'arialbd.ttf')))
pdfmetrics.registerFontFamily('ArialVN', normal='ArialVN', bold='ArialVN-Bold', italic='ArialVN', boldItalic='ArialVN-Bold')
navy = colors.HexColor('#112B4A')
blue = colors.HexColor('#1671D9')
body = ParagraphStyle('Body', fontName='ArialVN', fontSize=9.3, leading=14, spaceAfter=7, textColor=navy, splitLongWords=True)
title = ParagraphStyle('Title', parent=body, fontName='ArialVN-Bold', fontSize=22, leading=29, spaceAfter=16)
h2 = ParagraphStyle('H2', parent=body, fontName='ArialVN-Bold', fontSize=13, leading=18, spaceBefore=15, spaceAfter=9, keepWithNext=True)
h3 = ParagraphStyle('H3', parent=body, fontName='ArialVN-Bold', fontSize=10.5, leading=15, spaceBefore=9, spaceAfter=5, keepWithNext=True)
cell = ParagraphStyle('Cell', parent=body, fontSize=8, leading=11.5, spaceAfter=0)
head = ParagraphStyle('Head', parent=cell, fontName='ArialVN-Bold', textColor=colors.white)

def inline(text):
    text = text.replace('—','-').replace('–','-').replace('\u2011','-')
    text = escape(text)
    text = re.sub(r'\[([^\]]+)\]\((https?://[^)]+)\)', r'<link href="\2" color="#1671D9">\1</link>', text)
    text = re.sub(r'`([^`]+)`', r'<font color="#1671D9">\1</font>', text)
    text = re.sub(r'\*\*([^*]+)\*\*', r'<b>\1</b>', text)
    return text

lines = (root / 'docs/BACKEND-BUILD-SUPPLEMENT.md').read_text(encoding='utf-8').splitlines()
story = []
i = 0
while i < len(lines):
    line = lines[i].strip()
    if not line:
        i += 1
        continue
    if line.startswith('|'):
        rows = []
        while i < len(lines) and lines[i].strip().startswith('|'):
            values = [v.strip() for v in lines[i].strip().strip('|').split('|')]
            if not all(re.fullmatch(r':?-+:?', v) for v in values):
                rows.append(values)
            i += 1
        widths = [68, 203, 228] if rows[0][0] == 'Module' else [78, 165, 256]
        table = Table([[Paragraph(inline(v), head if index == 0 else cell) for v in row] for index, row in enumerate(rows)], colWidths=widths, repeatRows=1, hAlign='LEFT')
        table.setStyle(TableStyle([
            ('BACKGROUND',(0,0),(-1,0),navy),('VALIGN',(0,0),(-1,-1),'TOP'),
            ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.HexColor('#F1F5FA'),colors.white]),
            ('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),
            ('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),7),
            ('LINEBELOW',(0,0),(-1,-1),0.4,colors.HexColor('#DDE5EE')),
        ]))
        story.extend([table,Spacer(1,10)])
        continue
    if line.startswith('# '):
        story.append(Paragraph('SYNERA', ParagraphStyle('Brand', parent=h3, textColor=blue, fontSize=12, spaceAfter=8)))
        story.append(Paragraph(inline(line[2:].replace('SYNERA - ','')),title))
    elif line.startswith('## '):
        story.append(Paragraph(inline(line[3:]),h2))
    elif line.startswith('### '):
        story.append(Paragraph(inline(line[4:]),h3))
    else:
        story.append(Paragraph(inline(line),body))
    i += 1

def page(canvas, doc):
    canvas.saveState()
    w,h = A4
    canvas.setStrokeColor(colors.HexColor('#DDE5EE'))
    canvas.line(48,h-38,w-48,h-38)
    canvas.setFont('ArialVN',8)
    canvas.setFillColor(navy)
    canvas.drawString(48,h-29,'SYNERA / BACKEND / JAVA 25')
    canvas.drawString(48,28,'Tài liệu bổ sung - phạm vi triển khai và điều kiện nghiệm thu')
    canvas.drawRightString(w-48,28,str(doc.page))
    canvas.restoreState()

doc = SimpleDocTemplate(str(out),pagesize=A4,rightMargin=48,leftMargin=48,topMargin=52,bottomMargin=62,title='SYNERA - Bổ sung hướng dẫn build Backend',author='SYNERA',pageCompression=1)
doc.build(story,onFirstPage=page,onLaterPages=page)
print(out)

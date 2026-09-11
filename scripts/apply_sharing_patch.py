from pathlib import Path

ROOT = Path("app/src/main/java")


def patch(path: Path, replacements: list[tuple[str, str]], imports: list[str]):
    text = path.read_text(encoding="utf-8")
    for old, new in replacements:
        text = text.replace(old, new)
    marker = "import com.example.util.PdfReceiptGenerator"
    for imp in imports:
        if imp not in text:
            if marker in text:
                text = text.replace(marker, marker + "\n" + imp)
            else:
                # Safe fallback: insert after package/import block.
                lines = text.splitlines()
                idx = 1
                while idx < len(lines) and lines[idx].startswith("import "):
                    idx += 1
                lines.insert(idx, imp)
                text = "\n".join(lines) + ("\n" if text.endswith("\n") else "")
    path.write_text(text, encoding="utf-8")


def main():
    invoice = ROOT / "com/example/ui/components/InvoiceDetailDialog.kt"
    patch(invoice, [
        (
            'PdfReceiptGenerator.shareText(context, msg, "مشاركة الفاتورة عبر واتساب")',
            'ReceiptShareHelper.shareInvoiceToWhatsApp(context, invoice, items)'
        )
    ], ["import com.example.util.ReceiptShareHelper"])

    customers = ROOT / "com/example/ui/screens/CustomersScreen.kt"
    patch(customers, [
        (
            'PdfReceiptGenerator.shareText(context, textStatement, "إرسال كشف الحساب")',
            'ReceiptShareHelper.shareCustomerStatementToWhatsApp(context, customer, transactions)'
        )
    ], ["import com.example.util.ReceiptShareHelper"])


if __name__ == "__main__":
    main()

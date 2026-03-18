import os
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

SMTP_HOST = os.getenv("SMTP_HOST", "smtp-relay.brevo.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")
SMTP_FROM = os.getenv("SMTP_FROM", "")
APP_URL = os.getenv("APP_URL", "http://localhost:8000")
DEV_MODE = os.getenv("DEV_MODE", "true").lower() == "true"


def send_verification_email(to_email: str, token: str, name: str) -> None:
    """
    En mode DEV : affiche le lien dans le terminal.
    En mode PROD : envoie un vrai email via Brevo SMTP.
    """
    link = f"{APP_URL}/auth/verify-email?token={token}"

    if DEV_MODE:
        separator = "=" * 60
        print(f"\n{separator}")
        print(f"  [DEV] Email de vérification pour : {to_email}")
        print(f"  Copiez ce lien dans votre navigateur :")
        print(f"  {link}")
        print(f"{separator}\n")
        return

    msg = MIMEMultipart("alternative")
    msg["Subject"] = "Meet2Play — Vérifiez votre adresse email"
    msg["From"] = f"Meet2Play <{SMTP_FROM}>"
    msg["To"] = to_email

    html_body = f"""
    <!DOCTYPE html>
    <html lang="fr">
    <head><meta charset="UTF-8"></head>
    <body style="font-family: Arial, sans-serif; background:#f5f5f5; padding:40px;">
      <div style="max-width:480px; margin:auto; background:white; border-radius:16px;
                  padding:40px; box-shadow:0 2px 12px rgba(0,0,0,.08);">
        <h2 style="color:#0EA5E9; margin-top:0;">Bienvenue sur Meet2Play, {name} !</h2>
        <p style="color:#444; line-height:1.6;">
          Cliquez sur le bouton ci-dessous pour vérifier votre adresse email
          et activer votre compte.
        </p>
        <div style="text-align:center; margin:32px 0;">
          <a href="{link}"
             style="background:#0EA5E9; color:white; text-decoration:none;
                    padding:14px 32px; border-radius:8px; font-weight:bold;
                    font-size:15px; display:inline-block;">
            Vérifier mon email
          </a>
        </div>
        <p style="color:#888; font-size:12px;">
          Ce lien expire dans 24 heures.<br>
          Si vous n'avez pas créé de compte, ignorez cet email.
        </p>
      </div>
    </body>
    </html>
    """

    msg.attach(MIMEText(html_body, "html", "utf-8"))

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
        server.ehlo()
        server.starttls()
        server.login(SMTP_USER, SMTP_PASSWORD)
        server.sendmail(SMTP_USER, to_email, msg.as_string())

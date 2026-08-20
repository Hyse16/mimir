import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Mimir Backoffice",
  description: "Operational visibility for the Mimir personal assistant",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}

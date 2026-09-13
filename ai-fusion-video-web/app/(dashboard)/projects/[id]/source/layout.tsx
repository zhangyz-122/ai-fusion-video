import { MainContentFrame } from "@/components/dashboard/main-content-frame";

export default function ProjectSourceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <MainContentFrame>{children}</MainContentFrame>;
}

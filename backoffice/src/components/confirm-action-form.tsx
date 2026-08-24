"use client";

type ConfirmActionFormProps = {
  action: () => void | Promise<void>;
  label: string;
  message: string;
  tone?: "default" | "danger";
};

export function ConfirmActionForm({
  action,
  label,
  message,
  tone = "default",
}: ConfirmActionFormProps) {
  return (
    <form
      action={action}
      onSubmit={(event) => {
        if (!window.confirm(message)) event.preventDefault();
      }}
    >
      <button className={tone === "danger" ? "actionButton danger" : "actionButton"} type="submit">
        {label}
      </button>
    </form>
  );
}

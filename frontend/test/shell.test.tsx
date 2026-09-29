import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

function ShellStatus() {
  return (
    <main>
      <h1>Nexa Store</h1>
      <p role="status">Sẵn sàng nhận đơn hàng.</p>
    </main>
  );
}

describe("frontend test harness", () => {
  it("renders React in the shared DOM environment", () => {
    render(<ShellStatus />);

    expect(screen.getByRole("heading", { level: 1, name: "Nexa Store" })).toBeVisible();
    expect(screen.getByRole("status")).toHaveTextContent("Sẵn sàng nhận đơn hàng.");
  });
});

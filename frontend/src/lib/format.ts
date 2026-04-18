export function formatDateTime(value?: string) {
  if (!value) {
    return "Not available";
  }

  return new Intl.DateTimeFormat("ja-JP", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function formatNumber(value?: number, digits = 0) {
  if (value === undefined || Number.isNaN(value)) {
    return "-";
  }

  return new Intl.NumberFormat("ja-JP", {
    maximumFractionDigits: digits,
    minimumFractionDigits: digits,
  }).format(value);
}

export function formatCurrencyJpy(value?: number) {
  if (value === undefined || Number.isNaN(value)) {
    return "-";
  }

  return new Intl.NumberFormat("ja-JP", {
    style: "currency",
    currency: "JPY",
    maximumFractionDigits: 2,
  }).format(value);
}

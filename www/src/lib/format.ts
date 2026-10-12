const dayThisYear = new Intl.DateTimeFormat("en-AU", { month: "short", day: "numeric" });
const dayOtherYear = new Intl.DateTimeFormat("en-AU", { month: "short", day: "numeric", year: "numeric" });
const timeOfDay = new Intl.DateTimeFormat("en-AU", { hour: "numeric", minute: "2-digit" });

const startOfDay = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();

/** "Today", "Yesterday", or the date. */
export const dayLabel = (at: number, now = new Date()) => {
  const date = new Date(at);
  const days = Math.round((startOfDay(now) - startOfDay(date)) / 86_400_000);
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  return (date.getFullYear() === now.getFullYear() ? dayThisYear : dayOtherYear).format(date);
};

export const timeLabel = (at: number) => timeOfDay.format(new Date(at));

export const plural = (count: number, one: string, many = `${one}s`) => `${count} ${count === 1 ? one : many}`;

/** Corner radii for a row in a grouped list: large on the group's outer corners, small between rows. */
export const segRadius = (index: number, count: number) =>
  count === 1 ? "28px" : index === 0 ? "28px 28px 8px 8px" : index === count - 1 ? "8px 8px 28px 28px" : "8px";

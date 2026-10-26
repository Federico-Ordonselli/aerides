/** A game in a group's catalog. */
export interface Game {
  id: number;
  rawgId: number;
  name: string;
  slug: string;
  coverUrl: string | null;
}

/** A RAWG search hit, not yet added to a group. */
export interface GameSearchResult {
  rawgId: number;
  name: string;
  slug: string;
  coverUrl: string | null;
  released: string | null;
}

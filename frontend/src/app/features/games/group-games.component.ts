import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';

import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { Game, GameSearchResult } from './game.models';
import { GameSearchComponent } from './game-search.component';

/** The group's game catalog. OFFICER and OWNER can add games from RAWG and remove them. */
@Component({
  selector: 'app-group-games',
  imports: [TranslatePipe, MatButtonModule, MatCardModule, MatIconModule, MatTooltipModule, EmptyStateComponent, GameSearchComponent],
  templateUrl: './group-games.component.html',
  styleUrl: './group-games.component.scss',
})
export class GroupGamesComponent {
  readonly games = input.required<Game[]>();
  readonly canManage = input.required<boolean>();

  readonly add = output<GameSearchResult>();
  readonly remove = output<Game>();
}

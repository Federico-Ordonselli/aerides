import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { Member, MembershipRole } from './group.models';
import { MembersTableComponent } from './members-table.component';

const MEMBERS: Member[] = [
  { userId: 1, displayName: 'Aragorn', avatarUrl: null, role: 'OWNER', joinedAt: '2026-01-01T10:00:00Z' },
  { userId: 2, displayName: 'Legolas', avatarUrl: null, role: 'OFFICER', joinedAt: '2026-01-02T10:00:00Z' },
  { userId: 3, displayName: 'Gimli', avatarUrl: null, role: 'MEMBER', joinedAt: '2026-01-03T10:00:00Z' },
];

describe('MembersTableComponent', () => {
  async function render(myRole: MembershipRole, myUserId: number): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [MembersTableComponent],
      providers: [provideTranslateService()],
    });
    const fixture = TestBed.createComponent(MembersTableComponent);
    fixture.componentRef.setInput('members', MEMBERS);
    fixture.componentRef.setInput('myRole', myRole);
    fixture.componentRef.setInput('myUserId', myUserId);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('lets the owner manage everyone except themselves', async () => {
    const element = await render('OWNER', 1);

    expect(element.querySelectorAll('[data-testid="role-select"]')).toHaveLength(2);
    expect(element.querySelectorAll('[data-testid="remove-member"]')).toHaveLength(2);
  });

  it('shows no management controls to a member', async () => {
    const element = await render('MEMBER', 3);

    expect(element.querySelectorAll('[data-testid="role-select"]')).toHaveLength(0);
    expect(element.querySelectorAll('[data-testid="remove-member"]')).toHaveLength(0);
  });

  it('shows no management controls to an officer: only the owner manages roles', async () => {
    const element = await render('OFFICER', 2);

    expect(element.querySelectorAll('[data-testid="role-select"]')).toHaveLength(0);
  });
});

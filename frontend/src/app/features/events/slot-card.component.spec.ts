import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { Signup, SignupStatus, Slot } from './event.models';
import { SlotCardComponent } from './slot-card.component';

function signup(id: number, userId: number, name: string, status: SignupStatus): Signup {
  return { id, slotId: 1, userId, displayName: name, avatarUrl: null, status, position: id, createdAt: '2027-01-01T00:00:00Z' };
}

const FULL_SLOT: Slot = {
  id: 1,
  roleName: 'Tank',
  capacity: 1,
  confirmed: [signup(1, 10, 'Aragorn', 'CONFIRMED')],
  waitlisted: [signup(2, 11, 'Boromir', 'WAITLISTED'), signup(3, 12, 'Faramir', 'WAITLISTED')],
  pending: [signup(4, 13, 'Gollum', 'PENDING')],
};

describe('SlotCardComponent', () => {
  function render(inputs: { canSignUp?: boolean; canManage?: boolean }) {
    TestBed.configureTestingModule({ imports: [SlotCardComponent], providers: [provideTranslateService()] });
    const fixture = TestBed.createComponent(SlotCardComponent);
    fixture.componentRef.setInput('slot', FULL_SLOT);
    fixture.componentRef.setInput('myUserId', 12);
    fixture.componentRef.setInput('canSignUp', inputs.canSignUp ?? false);
    fixture.componentRef.setInput('canManage', inputs.canManage ?? false);
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('lists the waitlist in FIFO order', () => {
    const { element } = render({});

    const names = Array.from(element.querySelectorAll('[data-testid="waitlist"] li')).map((li) => li.textContent?.trim());
    expect(names).toEqual(['Boromir', 'Faramir']);
  });

  it('offers the waitlist when the slot is full', () => {
    const { fixture, element } = render({ canSignUp: true });
    const emitted: Slot[] = [];
    fixture.componentInstance.signUp.subscribe((slot) => emitted.push(slot));

    const button = element.querySelector('[data-testid="sign-up"]') as HTMLButtonElement;
    expect(button.textContent).toContain('events.detail.joinWaitlist');
    button.click();
    expect(emitted).toEqual([FULL_SLOT]);
  });

  it('shows approve and reject only to officers', () => {
    expect(render({ canManage: false }).element.querySelector('[data-testid="approve"]')).toBeNull();

    TestBed.resetTestingModule();
    const { fixture, element } = render({ canManage: true });
    const approved: Signup[] = [];
    fixture.componentInstance.approve.subscribe((value) => approved.push(value));
    (element.querySelector('[data-testid="approve"]') as HTMLButtonElement).click();
    expect(approved.map((value) => value.displayName)).toEqual(['Gollum']);
  });
});

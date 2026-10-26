import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function renderWithHealth(respond: (request: TestRequest) => void): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(App);
    respond(httpTesting.expectOne('/actuator/health'));
    await fixture.whenStable();
    return fixture.nativeElement;
  }

  it('announces that the bank is fictitious', async () => {
    const page = await renderWithHealth((request) => request.flush({ status: 'UP' }));
    expect(page.querySelector('.fictive-banner')?.textContent).toContain('Banque fictive');
  });

  it('shows the API as operational when health is UP', async () => {
    const page = await renderWithHealth((request) => request.flush({ status: 'UP' }));
    expect(page.querySelector('.status')?.textContent).toContain('API opérationnelle');
  });

  it('shows the API as unreachable when health fails', async () => {
    const page = await renderWithHealth((request) =>
      request.flush({ status: 'DOWN' }, { status: 503, statusText: 'Service Unavailable' }),
    );
    expect(page.querySelector('.status')?.textContent).toContain('API injoignable');
  });
});

import { TestBed } from '@angular/core/testing';

import { Endpoint } from './endpoint';

describe('Endpoint', () => {
  let service: Endpoint;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Endpoint);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
